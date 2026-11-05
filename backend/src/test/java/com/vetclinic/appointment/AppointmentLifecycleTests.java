package com.vetclinic.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.vetclinic.shared.error.DomainException;

class AppointmentLifecycleTests {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T10:00:00Z"), ZoneOffset.UTC);
	private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

	private final AppointmentLifecycle lifecycle = new AppointmentLifecycle();

	private static Appointment appt(AppointmentStatus status) {
		Appointment a = new Appointment(null, null, null, NOW.plusDays(1), NOW.plusDays(1).plusHours(1), status,
				Actor.RECEPTIONIST, BigDecimal.TEN, NOW);
		a.setReservedUntil(NOW.plusMinutes(5));
		return a;
	}

	private static TransitionContext ctx() {
		return new TransitionContext("because", NOW);
	}

	private record Row(AppointmentStatus from, AppointmentAction action, AppointmentStatus to, Set<Actor> actors) {
	}

	private static final List<Row> TABLE = List.of(
			new Row(AppointmentStatus.RESERVED, AppointmentAction.CONFIRM, AppointmentStatus.SCHEDULED,
					EnumSet.of(Actor.OWNER, Actor.RECEPTIONIST)),
			new Row(AppointmentStatus.RESERVED, AppointmentAction.CANCEL, AppointmentStatus.CANCELLED,
					EnumSet.of(Actor.OWNER, Actor.RECEPTIONIST)),
			new Row(AppointmentStatus.RESERVED, AppointmentAction.EXPIRE, AppointmentStatus.CANCELLED,
					EnumSet.of(Actor.SYSTEM)),
			new Row(AppointmentStatus.SCHEDULED, AppointmentAction.START, AppointmentStatus.IN_PROGRESS,
					EnumSet.of(Actor.VETERINARIAN, Actor.RECEPTIONIST)),
			new Row(AppointmentStatus.SCHEDULED, AppointmentAction.RESCHEDULE, AppointmentStatus.RESCHEDULED,
					EnumSet.of(Actor.RECEPTIONIST)),
			new Row(AppointmentStatus.SCHEDULED, AppointmentAction.CANCEL, AppointmentStatus.CANCELLED,
					EnumSet.of(Actor.OWNER, Actor.RECEPTIONIST)),
			new Row(AppointmentStatus.IN_PROGRESS, AppointmentAction.FINISH, AppointmentStatus.FINISHED,
					EnumSet.of(Actor.VETERINARIAN, Actor.RECEPTIONIST)),
			new Row(AppointmentStatus.IN_PROGRESS, AppointmentAction.RESCHEDULE, AppointmentStatus.RESCHEDULED,
					EnumSet.of(Actor.RECEPTIONIST)),
			new Row(AppointmentStatus.IN_PROGRESS, AppointmentAction.CANCEL, AppointmentStatus.CANCELLED,
					EnumSet.of(Actor.RECEPTIONIST)),
			new Row(AppointmentStatus.FINISHED, AppointmentAction.PAY, AppointmentStatus.PAID,
					EnumSet.of(Actor.RECEPTIONIST)),
			new Row(AppointmentStatus.FINISHED, AppointmentAction.CLOSE, AppointmentStatus.CLOSED,
					EnumSet.of(Actor.RECEPTIONIST)),
			new Row(AppointmentStatus.PAID, AppointmentAction.CLOSE, AppointmentStatus.CLOSED,
					EnumSet.of(Actor.RECEPTIONIST)));

	static Stream<Arguments> allowed() {
		return TABLE.stream()
			.flatMap(r -> r.actors().stream().map(a -> Arguments.of(r.from(), r.action(), r.to(), a)));
	}

	static Stream<Arguments> unlisted() {
		return Stream.of(AppointmentStatus.values())
			.flatMap(s -> Stream.of(AppointmentAction.values())
				.filter(a -> TABLE.stream().noneMatch(r -> r.from() == s && r.action() == a))
				.map(a -> Arguments.of(s, a)));
	}

	static Stream<Arguments> forbidden() {
		return TABLE.stream()
			.flatMap(r -> Stream.of(Actor.values())
				.filter(a -> !r.actors().contains(a))
				.map(a -> Arguments.of(r.from(), r.action(), a)));
	}

	@ParameterizedTest
	@MethodSource("allowed")
	void listedTransitionsSucceed(AppointmentStatus from, AppointmentAction action, AppointmentStatus to,
			Actor actor) {
		Appointment a = appt(from);
		lifecycle.apply(a, action, actor, ctx());
		assertThat(a.getStatus()).isEqualTo(to);
		if (action == AppointmentAction.CANCEL) {
			assertThat(a.getCancelReason()).isEqualTo("because");
		}
	}

	@ParameterizedTest
	@MethodSource("unlisted")
	void unlistedPairsAreInvalidTransitions(AppointmentStatus from, AppointmentAction action) {
		Appointment a = appt(from);
		for (Actor actor : Actor.values()) {
			assertThatThrownBy(() -> lifecycle.apply(a, action, actor, ctx())).isInstanceOfSatisfying(
					DomainException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_TRANSITION"));
		}
		assertThat(a.getStatus()).isEqualTo(from);
	}

	@ParameterizedTest
	@MethodSource("forbidden")
	void wrongActorIsNotAllowed(AppointmentStatus from, AppointmentAction action, Actor actor) {
		assertThatThrownBy(() -> lifecycle.apply(appt(from), action, actor, ctx())).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.getCode()).isEqualTo("ACTION_NOT_ALLOWED"));
	}

	@Test
	void cancelWithBlankReasonIsRejected() {
		for (String reason : new String[] { null, "", "   " }) {
			assertThatThrownBy(() -> lifecycle.apply(appt(AppointmentStatus.SCHEDULED), AppointmentAction.CANCEL,
					Actor.OWNER, new TransitionContext(reason, NOW)))
				.isInstanceOfSatisfying(DomainException.class,
						e -> assertThat(e.getCode()).isEqualTo("REASON_REQUIRED"));
		}
	}

	@Test
	void confirmAfterReservedUntilIsRejected() {
		Appointment a = appt(AppointmentStatus.RESERVED);
		a.setReservedUntil(NOW.minusSeconds(1));
		assertThatThrownBy(() -> lifecycle.apply(a, AppointmentAction.CONFIRM, Actor.OWNER, ctx()))
			.isInstanceOfSatisfying(DomainException.class,
					e -> assertThat(e.getCode()).isEqualTo("RESERVATION_EXPIRED"));
		assertThat(a.getStatus()).isEqualTo(AppointmentStatus.RESERVED);
	}

	@Test
	void allowedActionsPerRole() {
		assertThat(lifecycle.allowedActions(appt(AppointmentStatus.SCHEDULED), Actor.RECEPTIONIST)).containsExactlyInAnyOrder(
				AppointmentAction.START, AppointmentAction.CANCEL, AppointmentAction.RESCHEDULE,
				AppointmentAction.EDIT_OBSERVATIONS);
		assertThat(lifecycle.allowedActions(appt(AppointmentStatus.SCHEDULED), Actor.OWNER))
			.containsExactly(AppointmentAction.CANCEL);
		assertThat(lifecycle.allowedActions(appt(AppointmentStatus.RESERVED), Actor.SYSTEM)).isEmpty();
		assertThat(lifecycle.allowedActions(appt(AppointmentStatus.RESERVED), Actor.OWNER))
			.containsExactlyInAnyOrder(AppointmentAction.CONFIRM, AppointmentAction.CANCEL);
		assertThat(lifecycle.allowedActions(appt(AppointmentStatus.FINISHED), Actor.VETERINARIAN))
			.containsExactly(AppointmentAction.EDIT_OBSERVATIONS);
		assertThat(lifecycle.allowedActions(appt(AppointmentStatus.CLOSED), Actor.RECEPTIONIST)).isEmpty();
	}

	@Test
	void observationsNotEditableWhenClosedOrCancelled() {
		assertThat(lifecycle.canEditObservations(AppointmentStatus.CLOSED)).isFalse();
		assertThat(lifecycle.canEditObservations(AppointmentStatus.CANCELLED)).isFalse();
		assertThat(lifecycle.canEditObservations(AppointmentStatus.RESCHEDULED)).isFalse();
		assertThat(lifecycle.canEditObservations(AppointmentStatus.PAID)).isTrue();
	}
}
