package com.vetclinic.appointment;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vetclinic.appointment.dto.AppointmentResponse;
import com.vetclinic.appointment.dto.RescheduleRequest;
import com.vetclinic.auth.CurrentUser;
import com.vetclinic.auth.Role;
import com.vetclinic.shared.error.ConflictException;
import com.vetclinic.shared.error.ForbiddenException;

/** Status-changing use cases; every status change goes through AppointmentLifecycle. */
@Service
public class AppointmentActionService {

	private final AppointmentQueryService queries;
	private final AppointmentRepository appointments;
	private final AppointmentLifecycle lifecycle;
	private final BookingService booking;
	private final Clock clock;

	public AppointmentActionService(AppointmentQueryService queries, AppointmentRepository appointments,
			AppointmentLifecycle lifecycle, BookingService booking, Clock clock) {
		this.queries = queries;
		this.appointments = appointments;
		this.lifecycle = lifecycle;
		this.booking = booking;
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

	/** Cancels every RESERVED appointment whose hold has run out (TASK.md: unconfirmed reservations expire). */
	@Transactional
	public int expireDue() {
		LocalDateTime now = LocalDateTime.now(clock);
		List<Appointment> due = appointments.findByStatusAndReservedUntilLessThanEqual(AppointmentStatus.RESERVED,
				now);
		for (Appointment a : due) {
			lifecycle.apply(a, AppointmentAction.EXPIRE, Actor.SYSTEM, new TransitionContext("Reservation expired", now));
		}
		return due.size();
	}

	@Transactional
	public AppointmentResponse updateObservations(CurrentUser user, Long id, String text) {
		Appointment a = queries.requireScoped(user, id);
		if (user.role() == Role.OWNER) {
			throw new ForbiddenException("ACTION_NOT_ALLOWED", "Owners cannot edit observations");
		}
		if (!lifecycle.canEditObservations(a.getStatus())) {
			throw new ConflictException("OBSERVATIONS_LOCKED",
					"Observations cannot be edited in status " + a.getStatus());
		}
		a.setObservations(text);
		return queries.toResponse(appointments.save(a), user.actor());
	}

	/**
	 * Creates the replacement appointment first (same pet and service, total recomputed) and only then retires the
	 * original, so a slot conflict leaves the original untouched; the whole call is one transaction.
	 */
	@Transactional
	public AppointmentResponse reschedule(CurrentUser user, Long id, RescheduleRequest req) {
		Appointment original = queries.requireScoped(user, id);
		Appointment created = booking.createScheduled(original.getPet(), original.getService(),
				req.veterinarianId(), req.startAt(), Actor.RECEPTIONIST, original, original.getId());
		lifecycle.apply(original, AppointmentAction.RESCHEDULE, user.actor(),
				new TransitionContext(null, LocalDateTime.now(clock)));
		appointments.save(original);
		return queries.toResponse(created, user.actor());
	}

	private AppointmentResponse transition(CurrentUser user, Long id, AppointmentAction action, String reason) {
		Appointment a = queries.requireScoped(user, id);
		lifecycle.apply(a, action, user.actor(), new TransitionContext(reason, LocalDateTime.now(clock)));
		return queries.toResponse(appointments.save(a), user.actor());
	}
}
