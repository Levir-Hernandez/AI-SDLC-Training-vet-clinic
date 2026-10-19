package com.vetclinic.appointment;

import com.vetclinic.appointment.dto.AppointmentResponse;
import com.vetclinic.appointment.dto.RescheduleRequest;
import com.vetclinic.auth.CurrentUser;
import com.vetclinic.shared.error.ConflictException;
import com.vetclinic.shared.error.ForbiddenException;
import com.vetclinic.shared.error.NotFoundException;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lifecycle-driven actions: every status change goes through {@link AppointmentLifecycle}. */
@Service
public class AppointmentActionService {

    private final AppointmentRepository appointments;
    private final VeterinarianRepository veterinarians;
    private final AppointmentQueryService queries;
    private final BookingService booking;
    private final AppointmentLifecycle lifecycle;
    private final Clock clock;

    public AppointmentActionService(AppointmentRepository appointments, VeterinarianRepository veterinarians,
                                    AppointmentQueryService queries, BookingService booking,
                                    AppointmentLifecycle lifecycle, Clock clock) {
        this.appointments = appointments;
        this.veterinarians = veterinarians;
        this.queries = queries;
        this.booking = booking;
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    @Transactional
    public AppointmentResponse confirm(CurrentUser user, Long id) {
        return transition(user, id, AppointmentAction.CONFIRM, null);
    }

    @Transactional
    public AppointmentResponse cancel(CurrentUser user, Long id, String reason) {
        return transition(user, id, AppointmentAction.CANCEL, reason);
    }

    @Transactional
    public AppointmentResponse start(CurrentUser user, Long id) {
        return transition(user, id, AppointmentAction.START, null);
    }

    @Transactional
    public AppointmentResponse finish(CurrentUser user, Long id) {
        return transition(user, id, AppointmentAction.FINISH, null);
    }

    @Transactional
    public AppointmentResponse close(CurrentUser user, Long id) {
        return transition(user, id, AppointmentAction.CLOSE, null);
    }

    /** Cancels every reservation whose hold elapsed (TASK.md: unconfirmed reservation expires). Returns the count. */
    @Transactional
    public int expireDue() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Appointment> due = appointments.findByStatusAndReservedUntilLessThanEqual(AppointmentStatus.RESERVED, now);
        for (Appointment a : due) {
            lifecycle.apply(a, AppointmentAction.EXPIRE, Actor.SYSTEM, new TransitionContext(null, now));
        }
        return due.size();
    }

    @Transactional
    public AppointmentResponse updateObservations(CurrentUser user, Long id, String text) {
        if (user.actor() == Actor.OWNER) {
            throw new ForbiddenException("ACTION_NOT_ALLOWED", "Owners cannot edit observations");
        }
        Appointment a = queries.requireScoped(user, id);
        if (!lifecycle.canEditObservations(a.getStatus())) {
            throw new ConflictException("OBSERVATIONS_LOCKED",
                    "Observations cannot be edited in status " + a.getStatus());
        }
        a.setObservations(text);
        return queries.toResponse(a, user.actor());
    }

    /**
     * Reschedules (SCHEDULED) or reassigns (IN_PROGRESS) by creating a new SCHEDULED appointment linked to the
     * original, which becomes RESCHEDULED (A6). The transition is validated first and applied only after the
     * new slot is secured, so a conflict leaves the original unchanged.
     */
    @Transactional
    public AppointmentResponse reschedule(CurrentUser user, Long id, RescheduleRequest request) {
        Appointment original = queries.requireScoped(user, id);
        Actor actor = user.actor();
        TransitionContext ctx = new TransitionContext(null, LocalDateTime.now(clock));
        lifecycle.validate(original, AppointmentAction.RESCHEDULE, actor, ctx);
        Veterinarian vet = request.veterinarianId() == null || request.veterinarianId().equals(original.getVeterinarian().getId())
                ? original.getVeterinarian()
                : veterinarians.findById(request.veterinarianId()).orElseThrow(() ->
                        new NotFoundException("VETERINARIAN_NOT_FOUND",
                                "Veterinarian " + request.veterinarianId() + " not found"));
        Appointment created = booking.createScheduled(original.getPet(), original.getService(), vet,
                request.startAt(), Actor.RECEPTIONIST, original, original.getId());
        lifecycle.apply(original, AppointmentAction.RESCHEDULE, actor, ctx);
        return queries.toResponse(created, actor);
    }

    private AppointmentResponse transition(CurrentUser user, Long id, AppointmentAction action, String reason) {
        Appointment a = queries.requireScoped(user, id);
        lifecycle.apply(a, action, user.actor(), new TransitionContext(reason, LocalDateTime.now(clock)));
        return queries.toResponse(a, user.actor());
    }
}
