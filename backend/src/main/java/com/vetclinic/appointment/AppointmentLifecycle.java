package com.vetclinic.appointment;

import static com.vetclinic.appointment.Actor.OWNER;
import static com.vetclinic.appointment.Actor.RECEPTIONIST;
import static com.vetclinic.appointment.Actor.SYSTEM;
import static com.vetclinic.appointment.Actor.VETERINARIAN;
import static com.vetclinic.appointment.AppointmentAction.CANCEL;
import static com.vetclinic.appointment.AppointmentAction.CLOSE;
import static com.vetclinic.appointment.AppointmentAction.CONFIRM;
import static com.vetclinic.appointment.AppointmentAction.EDIT_OBSERVATIONS;
import static com.vetclinic.appointment.AppointmentAction.EXPIRE;
import static com.vetclinic.appointment.AppointmentAction.FINISH;
import static com.vetclinic.appointment.AppointmentAction.PAY;
import static com.vetclinic.appointment.AppointmentAction.RESCHEDULE;
import static com.vetclinic.appointment.AppointmentAction.START;
import static com.vetclinic.appointment.AppointmentStatus.CANCELLED;
import static com.vetclinic.appointment.AppointmentStatus.CLOSED;
import static com.vetclinic.appointment.AppointmentStatus.FINISHED;
import static com.vetclinic.appointment.AppointmentStatus.IN_PROGRESS;
import static com.vetclinic.appointment.AppointmentStatus.PAID;
import static com.vetclinic.appointment.AppointmentStatus.RESCHEDULED;
import static com.vetclinic.appointment.AppointmentStatus.RESERVED;
import static com.vetclinic.appointment.AppointmentStatus.SCHEDULED;

import com.vetclinic.shared.error.BadRequestException;
import com.vetclinic.shared.error.ConflictException;
import com.vetclinic.shared.error.ForbiddenException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Single owner of the TASK.md transition table. Every status change and every allowed-actions list
 * goes through this component.
 */
@Component
public class AppointmentLifecycle {

    /** One row of the transition table. */
    public record Rule(AppointmentStatus from, AppointmentAction action, AppointmentStatus to, Set<Actor> actors) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(RESERVED, CONFIRM, SCHEDULED, EnumSet.of(OWNER, RECEPTIONIST)),   // requires reservedUntil > now
            new Rule(RESERVED, CANCEL, CANCELLED, EnumSet.of(OWNER, RECEPTIONIST)),    // reason required
            new Rule(RESERVED, EXPIRE, CANCELLED, EnumSet.of(SYSTEM)),
            new Rule(SCHEDULED, START, IN_PROGRESS, EnumSet.of(VETERINARIAN, RECEPTIONIST)),
            new Rule(SCHEDULED, RESCHEDULE, RESCHEDULED, EnumSet.of(RECEPTIONIST)),
            new Rule(SCHEDULED, CANCEL, CANCELLED, EnumSet.of(OWNER, RECEPTIONIST)),   // reason required
            new Rule(IN_PROGRESS, FINISH, FINISHED, EnumSet.of(VETERINARIAN, RECEPTIONIST)),
            new Rule(IN_PROGRESS, RESCHEDULE, RESCHEDULED, EnumSet.of(RECEPTIONIST)),  // reassign
            new Rule(IN_PROGRESS, CANCEL, CANCELLED, EnumSet.of(RECEPTIONIST)),        // reason required
            new Rule(FINISHED, PAY, PAID, EnumSet.of(RECEPTIONIST)),
            new Rule(FINISHED, CLOSE, CLOSED, EnumSet.of(RECEPTIONIST)),               // closes with debt
            new Rule(PAID, CLOSE, CLOSED, EnumSet.of(RECEPTIONIST)));

    public static List<Rule> rules() {
        return RULES;
    }

    /** Applies the transition; fails with the same errors as {@link #validate} and changes nothing on failure. */
    public void apply(Appointment appointment, AppointmentAction action, Actor actor, TransitionContext ctx) {
        Rule rule = resolve(appointment, action, actor, ctx);
        appointment.applyStatus(rule.to(), reasonFor(action, ctx));
    }

    /** Checks that the transition is allowed without changing the appointment. */
    public void validate(Appointment appointment, AppointmentAction action, Actor actor, TransitionContext ctx) {
        resolve(appointment, action, actor, ctx);
    }

    private Rule resolve(Appointment appointment, AppointmentAction action, Actor actor, TransitionContext ctx) {
        AppointmentStatus from = appointment.getStatus();
        Rule rule = RULES.stream()
                .filter(r -> r.from() == from && r.action() == action)
                .findFirst()
                .orElseThrow(() -> new ConflictException("INVALID_TRANSITION",
                        "Cannot " + action + " an appointment in status " + from));
        if (!rule.actors().contains(actor)) {
            throw new ForbiddenException("ACTION_NOT_ALLOWED", actor + " cannot " + action + " an appointment");
        }
        if (action == CANCEL) {
            if (reasonFor(action, ctx).isEmpty()) {
                throw new BadRequestException("REASON_REQUIRED", "A cancellation reason is required");
            }
        } else if (action == CONFIRM
                && (appointment.getReservedUntil() == null || !appointment.getReservedUntil().isAfter(ctx.now()))) {
            throw new ConflictException("RESERVATION_EXPIRED", "The reservation has expired");
        }
        return rule;
    }

    private static String reasonFor(AppointmentAction action, TransitionContext ctx) {
        if (action == CANCEL) {
            return ctx.reason() == null ? "" : ctx.reason().trim();
        }
        return action == EXPIRE ? "Reservation expired" : null;
    }

    /** Observations can be edited until the appointment is closed or cancelled (TASK.md). */
    public boolean canEditObservations(AppointmentStatus status) {
        return !status.isFinal();
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
