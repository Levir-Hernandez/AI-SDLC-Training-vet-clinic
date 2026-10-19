package com.vetclinic.appointment;

import com.vetclinic.appointment.dto.AppointmentResponse;
import com.vetclinic.appointment.dto.CreateAppointmentRequest;
import com.vetclinic.auth.CurrentUser;
import com.vetclinic.catalog.CatalogService;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.PetService;
import com.vetclinic.shared.error.BadRequestException;
import com.vetclinic.shared.error.ConflictException;
import com.vetclinic.shared.error.ForbiddenException;
import com.vetclinic.shared.error.NotFoundException;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final PetService petService;
    private final CatalogService catalog;
    private final VeterinarianRepository veterinarians;
    private final AppointmentRepository appointments;
    private final AvailabilityService availability;
    private final AppointmentQueryService queries;
    private final Clock clock;
    private final Duration reservationTtl;

    public BookingService(PetService petService, CatalogService catalog, VeterinarianRepository veterinarians,
                          AppointmentRepository appointments, AvailabilityService availability,
                          AppointmentQueryService queries, Clock clock,
                          @Value("${app.booking.reservation-ttl:PT10M}") Duration reservationTtl) {
        this.petService = petService;
        this.catalog = catalog;
        this.veterinarians = veterinarians;
        this.appointments = appointments;
        this.availability = availability;
        this.queries = queries;
        this.clock = clock;
        this.reservationTtl = reservationTtl;
    }

    /** Reception books a walk-in (SCHEDULED); an owner reserves for own pet (RESERVED); veterinarians cannot book. */
    @Transactional
    public AppointmentResponse book(CurrentUser user, CreateAppointmentRequest request) {
        Actor actor = user.actor();
        if (actor == Actor.VETERINARIAN) {
            throw new ForbiddenException("ACTION_NOT_ALLOWED", "Veterinarians cannot book appointments");
        }
        Pet pet = petService.requireAccessible(user, request.petId());
        ClinicService service = catalog.require(request.serviceId());
        Veterinarian vet = veterinarians.findById(request.veterinarianId()).orElseThrow(() ->
                new NotFoundException("VETERINARIAN_NOT_FOUND",
                        "Veterinarian " + request.veterinarianId() + " not found"));
        Appointment appointment;
        if (actor == Actor.OWNER) {
            LocalDateTime reservedUntil = LocalDateTime.now(clock).plus(reservationTtl);
            appointment = create(pet, service, vet, request.startAt(), AppointmentStatus.RESERVED, Actor.OWNER,
                    reservedUntil, null, null);
        } else {
            appointment = createScheduled(pet, service, vet, request.startAt(), Actor.RECEPTIONIST, null, null);
        }
        return queries.toResponse(appointment, actor);
    }

    /** Shared by walk-in booking and reschedule; the total is fixed here (base price + veterinarian cost). */
    @Transactional
    public Appointment createScheduled(Pet pet, ClinicService service, Veterinarian vet, LocalDateTime start,
                                       Actor bookedBy, Appointment rescheduledFrom, Long excludeId) {
        return create(pet, service, vet, start, AppointmentStatus.SCHEDULED, bookedBy, null, rescheduledFrom,
                excludeId);
    }

    private Appointment create(Pet pet, ClinicService service, Veterinarian requestedVet, LocalDateTime start,
                               AppointmentStatus status, Actor bookedBy, LocalDateTime reservedUntil,
                               Appointment rescheduledFrom, Long excludeId) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!start.isAfter(now)) {
            throw new BadRequestException("START_IN_PAST", "Appointments must start in the future");
        }
        // Row lock serializes concurrent bookings of one veterinarian; the overlap re-check runs after it
        Veterinarian vet = veterinarians.findLockedById(requestedVet.getId()).orElseThrow(() ->
                new NotFoundException("VETERINARIAN_NOT_FOUND", "Veterinarian " + requestedVet.getId() + " not found"));
        if (!availability.isBookable(vet, service, start)) {
            throw new ConflictException("SLOT_UNAVAILABLE", "The veterinarian is not available at that time");
        }
        LocalDateTime end = start.plusMinutes(service.getDurationMinutes());
        if (appointments.existsOverlap(vet.getId(), start, end, now, excludeId)) {
            throw new ConflictException("SLOT_TAKEN", "That time slot is already taken");
        }
        return appointments.save(new Appointment(pet, service, vet, start, status, bookedBy, reservedUntil,
                rescheduledFrom, now));
    }
}
