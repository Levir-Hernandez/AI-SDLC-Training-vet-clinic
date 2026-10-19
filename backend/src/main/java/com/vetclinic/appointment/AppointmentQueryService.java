package com.vetclinic.appointment;

import com.vetclinic.appointment.dto.AppointmentFilter;
import com.vetclinic.appointment.dto.AppointmentResponse;
import com.vetclinic.auth.CurrentUser;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.PetService;
import com.vetclinic.payment.Payment;
import com.vetclinic.payment.PaymentRepository;
import com.vetclinic.shared.PageResponse;
import com.vetclinic.shared.error.NotFoundException;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Scoped reads: owners see their pets' appointments, veterinarians their assigned ones, reception all (TASK.md). */
@Service
@Transactional(readOnly = true)
public class AppointmentQueryService {

    private final AppointmentRepository appointments;
    private final PaymentRepository payments;
    private final PetService petService;
    private final AppointmentLifecycle lifecycle;
    private final Clock clock;

    public AppointmentQueryService(AppointmentRepository appointments, PaymentRepository payments,
                                   PetService petService, AppointmentLifecycle lifecycle, Clock clock) {
        this.appointments = appointments;
        this.payments = payments;
        this.petService = petService;
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    public PageResponse<AppointmentResponse> list(CurrentUser user, AppointmentFilter filter, Pageable pageable) {
        AppointmentFilter f = filter == null ? AppointmentFilter.none() : filter;
        Specification<Appointment> spec = scope(user);
        if (f.statuses() != null && !f.statuses().isEmpty()) {
            spec = spec.and((root, q, cb) -> root.get("status").in(f.statuses()));
        }
        if (f.date() != null) {
            LocalDateTime from = f.date().atStartOfDay();
            LocalDateTime to = f.date().plusDays(1).atStartOfDay();
            spec = spec.and((root, q, cb) -> cb.and(cb.greaterThanOrEqualTo(root.get("startAt"), from),
                    cb.lessThan(root.get("startAt"), to)));
        }
        if (f.veterinarianId() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("veterinarian").get("id"), f.veterinarianId()));
        }
        if (Boolean.TRUE.equals(f.debt())) {
            spec = spec.and(debtSpec());
        }
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("startAt"));
        Actor actor = user.actor();
        return PageResponse.from(appointments.findAll(spec, sorted).map(a -> toResponse(a, actor)));
    }

    public AppointmentResponse get(CurrentUser user, Long id) {
        return toResponse(requireScoped(user, id), user.actor());
    }

    /** Out-of-scope appointments are reported as not found so other users' data is not revealed. */
    public Appointment requireScoped(CurrentUser user, Long id) {
        Appointment a = appointments.findById(id).orElseThrow(() -> notFound(id));
        boolean inScope = switch (user.role()) {
            case OWNER -> a.getPet().getOwner().getId().equals(user.ownerId());
            case VETERINARIAN -> a.getVeterinarian().getId().equals(user.veterinarianId());
            case RECEPTIONIST -> true;
        };
        if (!inScope) {
            throw notFound(id);
        }
        return a;
    }

    /** Service history of a pet, newest first, with an {@code upcoming} flag per entry (TASK.md Follow up). */
    public PageResponse<AppointmentResponse> historyForPet(CurrentUser user, Long petId, Pageable pageable) {
        Pet pet = petService.requireAccessible(user, petId);
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "startAt"));
        Actor actor = user.actor();
        return PageResponse.from(appointments.findByPetId(pet.getId(), sorted).map(a -> toResponse(a, actor)));
    }

    public AppointmentResponse toResponse(Appointment a, Actor actor) {
        LocalDateTime now = LocalDateTime.now(clock);
        Pet pet = a.getPet();
        Long rescheduledToId = appointments.findByRescheduledFromId(a.getId()).stream()
                .map(Appointment::getId).findFirst().orElse(null);
        boolean debt = a.getStatus() == AppointmentStatus.CLOSED && !payments.existsByAppointmentId(a.getId());
        List<AppointmentAction> actions = new ArrayList<>(lifecycle.allowedActions(a, actor));
        actions.sort(Comparator.naturalOrder());
        return new AppointmentResponse(
                a.getId(),
                new AppointmentResponse.PetSummary(pet.getId(), pet.getName(), pet.getSpecies(), pet.getBreed()),
                pet.getOwner().getName(),
                new AppointmentResponse.ServiceSummary(a.getService().getId(), a.getService().getName(),
                        a.getService().getDurationMinutes()),
                new AppointmentResponse.VeterinarianSummary(a.getVeterinarian().getId(), a.getVeterinarian().getName()),
                a.getStartAt(), a.getEndAt(), a.getStatus(), a.getBookedBy(), a.getTotal(), a.getObservations(),
                a.getCancelReason(), a.getReservedUntil(),
                a.getRescheduledFrom() == null ? null : a.getRescheduledFrom().getId(),
                rescheduledToId, debt, a.getStartAt().isAfter(now), actions);
    }

    private Specification<Appointment> scope(CurrentUser user) {
        return switch (user.role()) {
            case OWNER -> (root, q, cb) -> cb.equal(root.get("pet").get("owner").get("id"), user.ownerId());
            case VETERINARIAN -> (root, q, cb) -> cb.equal(root.get("veterinarian").get("id"), user.veterinarianId());
            case RECEPTIONIST -> (root, q, cb) -> cb.conjunction();
        };
    }

    /** A5: debt = CLOSED with no payment. */
    private Specification<Appointment> debtSpec() {
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<Payment> p = sub.from(Payment.class);
            sub.select(p.get("id")).where(cb.equal(p.get("appointment"), root));
            return cb.and(cb.equal(root.get("status"), AppointmentStatus.CLOSED), cb.not(cb.exists(sub)));
        };
    }

    private NotFoundException notFound(Long id) {
        return new NotFoundException("APPOINTMENT_NOT_FOUND", "Appointment " + id + " not found");
    }
}
