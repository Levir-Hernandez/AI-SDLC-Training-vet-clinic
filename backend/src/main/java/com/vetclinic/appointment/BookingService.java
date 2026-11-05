package com.vetclinic.appointment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
public class BookingService {

	private final PetService petService;
	private final CatalogService catalog;
	private final VeterinarianRepository vets;
	private final AppointmentRepository appointments;
	private final AvailabilityService availability;
	private final AppointmentQueryService queries;
	private final Clock clock;
	private final Duration reservationTtl;

	public BookingService(PetService petService, CatalogService catalog, VeterinarianRepository vets,
			AppointmentRepository appointments, AvailabilityService availability, AppointmentQueryService queries,
			Clock clock, @Value("${app.booking.reservation-ttl:PT10M}") Duration reservationTtl) {
		this.petService = petService;
		this.catalog = catalog;
		this.vets = vets;
		this.appointments = appointments;
		this.availability = availability;
		this.queries = queries;
		this.clock = clock;
		this.reservationTtl = reservationTtl;
	}

	/** Receptionist books a walk-in (SCHEDULED); an owner books online (RESERVED for the TTL). */
	@Transactional
	public AppointmentResponse book(CurrentUser user, CreateAppointmentRequest req) {
		Actor actor = user.actor();
		if (actor != Actor.RECEPTIONIST && actor != Actor.OWNER) {
			throw new ForbiddenException("ACTION_NOT_ALLOWED", "Veterinarians cannot book appointments");
		}
		Pet pet = petService.requireAccessible(user, req.petId());
		ClinicService service = catalog.require(req.serviceId());
		AppointmentStatus status = actor == Actor.OWNER ? AppointmentStatus.RESERVED : AppointmentStatus.SCHEDULED;
		Appointment saved = create(pet, service, req.veterinarianId(), req.startAt(), status, actor, null, null);
		return queries.toResponse(saved, actor);
	}

	@Transactional
	public Appointment createScheduled(Pet pet, ClinicService service, Long vetId, LocalDateTime start,
			Actor bookedBy, Appointment rescheduledFrom, Long excludeId) {
		return create(pet, service, vetId, start, AppointmentStatus.SCHEDULED, bookedBy, rescheduledFrom, excludeId);
	}

	private Appointment create(Pet pet, ClinicService service, Long vetId, LocalDateTime start,
			AppointmentStatus status, Actor bookedBy, Appointment rescheduledFrom, Long excludeId) {
		LocalDateTime now = LocalDateTime.now(clock);
		if (!start.isAfter(now)) {
			throw new BadRequestException("START_IN_PAST", "Appointments must be booked in the future");
		}
		// Row lock serializes concurrent bookings of the same veterinarian (TASK.md: no double booking).
		Veterinarian vet = vets.findLockedById(vetId)
			.orElseThrow(() -> new NotFoundException("VETERINARIAN_NOT_FOUND", "Veterinarian " + vetId + " not found"));
		if (!availability.isBookable(vet, service, start)) {
			throw new ConflictException("SLOT_UNAVAILABLE", "The slot is outside the veterinarian's schedule");
		}
		LocalDateTime end = start.plusMinutes(service.getDurationMinutes());
		if (appointments.existsOverlap(vet.getId(), start, end, now, excludeId)) {
			throw new ConflictException("SLOT_TAKEN", "The slot is already taken");
		}
		// Total is fixed at booking time: base price + veterinarian cost.
		BigDecimal total = service.getBasePrice().add(vet.getCostPerService()).setScale(2, RoundingMode.HALF_UP);
		Appointment a = new Appointment(pet, service, vet, start, end, status, bookedBy, total, now);
		if (status == AppointmentStatus.RESERVED) {
			a.setReservedUntil(now.plus(reservationTtl));
		}
		a.setRescheduledFrom(rescheduledFrom);
		return appointments.saveAndFlush(a);
	}
}
