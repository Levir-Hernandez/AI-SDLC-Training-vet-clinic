package com.vetclinic.appointment;

import static com.vetclinic.appointment.Actor.OWNER;
import static com.vetclinic.appointment.Actor.RECEPTIONIST;
import static com.vetclinic.appointment.Actor.SYSTEM;
import static com.vetclinic.appointment.Actor.VETERINARIAN;
import static com.vetclinic.appointment.AppointmentAction.*;
import static com.vetclinic.appointment.AppointmentStatus.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.vetclinic.shared.error.BadRequestException;
import com.vetclinic.shared.error.ConflictException;
import com.vetclinic.shared.error.ForbiddenException;

/** Single place enforcing the TASK.md appointment transition table. */
@Component
public class AppointmentLifecycle {

	record Rule(AppointmentStatus from, AppointmentAction action, AppointmentStatus to, Set<Actor> actors) {
	}

	private static final List<Rule> RULES = List.of(
			new Rule(RESERVED, CONFIRM, SCHEDULED, EnumSet.of(Actor.OWNER, RECEPTIONIST)),
			new Rule(RESERVED, CANCEL, CANCELLED, EnumSet.of(Actor.OWNER, RECEPTIONIST)),
			new Rule(RESERVED, EXPIRE, CANCELLED, EnumSet.of(SYSTEM)),
			new Rule(SCHEDULED, START, IN_PROGRESS, EnumSet.of(VETERINARIAN, RECEPTIONIST)),
			new Rule(SCHEDULED, RESCHEDULE, RESCHEDULED, EnumSet.of(RECEPTIONIST)),
			new Rule(SCHEDULED, CANCEL, CANCELLED, EnumSet.of(Actor.OWNER, RECEPTIONIST)),
			new Rule(IN_PROGRESS, FINISH, FINISHED, EnumSet.of(VETERINARIAN, RECEPTIONIST)),
			new Rule(IN_PROGRESS, RESCHEDULE, RESCHEDULED, EnumSet.of(RECEPTIONIST)),
			new Rule(IN_PROGRESS, CANCEL, CANCELLED, EnumSet.of(RECEPTIONIST)),
			new Rule(FINISHED, PAY, PAID, EnumSet.of(RECEPTIONIST)),
			new Rule(FINISHED, CLOSE, CLOSED, EnumSet.of(RECEPTIONIST)),
			new Rule(PAID, CLOSE, CLOSED, EnumSet.of(RECEPTIONIST)));

	public void apply(Appointment appointment, AppointmentAction action, Actor actor, TransitionContext ctx) {
		AppointmentStatus current = appointment.getStatus();
		Rule rule = RULES.stream()
			.filter(r -> r.from() == current && r.action() == action)
			.findFirst()
			.orElseThrow(() -> new ConflictException("INVALID_TRANSITION",
					"Cannot " + action + " an appointment in status " + current));
		if (!rule.actors().contains(actor)) {
			throw new ForbiddenException("ACTION_NOT_ALLOWED", actor + " cannot " + action + " an appointment");
		}
		String reason = null;
		if (action == CANCEL) {
			if (ctx.reason() == null || ctx.reason().isBlank()) {
				throw new BadRequestException("REASON_REQUIRED", "A cancellation reason is required");
			}
			reason = ctx.reason().trim();
		}
		else if (action == EXPIRE) {
			reason = ctx.reason();
		}
		if (action == CONFIRM) {
			if (appointment.getReservedUntil() == null || !appointment.getReservedUntil().isAfter(ctx.now())) {
				throw new ConflictException("RESERVATION_EXPIRED", "The reservation has expired");
			}
		}
		appointment.applyStatus(rule.to(), reason);
	}

	public boolean canEditObservations(AppointmentStatus status) {
		return status != CLOSED && status != CANCELLED && status != RESCHEDULED;
	}

	public Set<AppointmentAction> allowedActions(Appointment appointment, Actor actor) {
		AppointmentStatus status = appointment.getStatus();
		Set<AppointmentAction> result = EnumSet.noneOf(AppointmentAction.class);
		for (Rule r : RULES) {
			if (r.from() == status && r.action() != EXPIRE && r.actors().contains(actor)) {
				result.add(r.action());
			}
		}
		if ((actor == VETERINARIAN || actor == RECEPTIONIST) && canEditObservations(status)) {
			result.add(EDIT_OBSERVATIONS);
		}
		return result;
	}
}
