package com.vetclinic.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ServiceCode;
import com.vetclinic.owner.Breed;
import com.vetclinic.owner.Owner;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.Species;
import com.vetclinic.shared.error.DomainException;
import com.vetclinic.veterinarian.ScheduleBlock;
import com.vetclinic.veterinarian.Veterinarian;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static com.vetclinic.appointment.Actor.*;
import static com.vetclinic.appointment.AppointmentAction.*;
import static com.vetclinic.appointment.AppointmentStatus.*;

class AppointmentLifecycleTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 2, 10, 0);

    private final AppointmentLifecycle lifecycle = new AppointmentLifecycle();

    /** Expected table, written independently of the production rules: from, action, to, actors. */
    private record Row(AppointmentStatus from, AppointmentAction action, AppointmentStatus to, Set<Actor> actors) {
    }

    private static final List<Row> TABLE = List.of(
            new Row(RESERVED, CONFIRM, SCHEDULED, EnumSet.of(OWNER, RECEPTIONIST)),
            new Row(RESERVED, CANCEL, CANCELLED, EnumSet.of(OWNER, RECEPTIONIST)),
            new Row(RESERVED, EXPIRE, CANCELLED, EnumSet.of(SYSTEM)),
            new Row(SCHEDULED, START, IN_PROGRESS, EnumSet.of(VETERINARIAN, RECEPTIONIST)),
            new Row(SCHEDULED, RESCHEDULE, RESCHEDULED, EnumSet.of(RECEPTIONIST)),
            new Row(SCHEDULED, CANCEL, CANCELLED, EnumSet.of(OWNER, RECEPTIONIST)),
            new Row(IN_PROGRESS, FINISH, FINISHED, EnumSet.of(VETERINARIAN, RECEPTIONIST)),
            new Row(IN_PROGRESS, RESCHEDULE, RESCHEDULED, EnumSet.of(RECEPTIONIST)),
            new Row(IN_PROGRESS, CANCEL, CANCELLED, EnumSet.of(RECEPTIONIST)),
            new Row(FINISHED, PAY, PAID, EnumSet.of(RECEPTIONIST)),
            new Row(FINISHED, CLOSE, CLOSED, EnumSet.of(RECEPTIONIST)),
            new Row(PAID, CLOSE, CLOSED, EnumSet.of(RECEPTIONIST)));

    private static final Set<AppointmentAction> TRANSITION_ACTIONS =
            EnumSet.complementOf(EnumSet.of(EDIT_OBSERVATIONS));

    private static Appointment appointment(AppointmentStatus status) {
        Owner owner = new Owner("Ann", "555-0001");
        Pet pet = new Pet("Rex", Species.DOG, Breed.BULLDOG, owner);
        ClinicService service = new ClinicService(ServiceCode.GROOMING, "Grooming", 60, new BigDecimal("35.00"));
        Veterinarian vet = new Veterinarian("Ana", "General", new BigDecimal("20.00"),
                List.of(new ScheduleBlock(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0))));
        return new Appointment(pet, service, vet, NOW.plusDays(1), status, RECEPTIONIST,
                status == RESERVED ? NOW.plusMinutes(10) : null, null, NOW);
    }

    private static TransitionContext ctx() {
        return new TransitionContext("Owner request", NOW);
    }

    static Stream<Arguments> allowedRows() {
        return TABLE.stream().flatMap(r -> r.actors().stream().map(a -> Arguments.of(r.from(), r.action(), r.to(), a)));
    }

    static Stream<Arguments> unlistedPairs() {
        return Stream.of(AppointmentStatus.values()).flatMap(s -> TRANSITION_ACTIONS.stream()
                .filter(a -> TABLE.stream().noneMatch(r -> r.from() == s && r.action() == a))
                .map(a -> Arguments.of(s, a)));
    }

    static Stream<Arguments> forbiddenActors() {
        return TABLE.stream().flatMap(r -> EnumSet.complementOf(EnumSet.copyOf(r.actors())).stream()
                .map(a -> Arguments.of(r.from(), r.action(), a)));
    }

    @ParameterizedTest
    @MethodSource("allowedRows")
    void listedTransitionSucceedsForListedActor(AppointmentStatus from, AppointmentAction action,
                                                AppointmentStatus to, Actor actor) {
        Appointment a = appointment(from);
        lifecycle.apply(a, action, actor, ctx());
        assertThat(a.getStatus()).isEqualTo(to);
    }

    @ParameterizedTest
    @MethodSource("unlistedPairs")
    void unlistedStatusActionPairIsInvalidTransition(AppointmentStatus from, AppointmentAction action) {
        Appointment a = appointment(from);
        for (Actor actor : Actor.values()) {
            assertThatThrownBy(() -> lifecycle.apply(a, action, actor, ctx()))
                    .isInstanceOfSatisfying(DomainException.class,
                            e -> assertThat(e.getCode()).isEqualTo("INVALID_TRANSITION"));
            assertThat(a.getStatus()).isEqualTo(from);
        }
    }

    @ParameterizedTest
    @MethodSource("forbiddenActors")
    void unlistedActorIsNotAllowed(AppointmentStatus from, AppointmentAction action, Actor actor) {
        Appointment a = appointment(from);
        assertThatThrownBy(() -> lifecycle.apply(a, action, actor, ctx()))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ACTION_NOT_ALLOWED"));
        assertThat(a.getStatus()).isEqualTo(from);
    }

    @Test
    void invalidTransitionMessageNamesActionAndStatus() {
        assertThatThrownBy(() -> lifecycle.apply(appointment(CLOSED), START, RECEPTIONIST, ctx()))
                .hasMessage("Cannot START an appointment in status CLOSED");
    }

    @Test
    void ownerCannotStart() {
        assertThatThrownBy(() -> lifecycle.apply(appointment(SCHEDULED), START, OWNER, ctx()))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getCode()).isEqualTo("ACTION_NOT_ALLOWED"));
    }

    @Test
    void ownerCannotCancelInProgress() {
        assertThatThrownBy(() -> lifecycle.apply(appointment(IN_PROGRESS), CANCEL, OWNER, ctx()))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getCode()).isEqualTo("ACTION_NOT_ALLOWED"));
    }

    @Test
    void veterinarianCannotPay() {
        assertThatThrownBy(() -> lifecycle.apply(appointment(FINISHED), PAY, VETERINARIAN, ctx()))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getCode()).isEqualTo("ACTION_NOT_ALLOWED"));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"   "})
    void cancelWithoutReasonIsRejected(String reason) {
        Appointment a = appointment(SCHEDULED);
        assertThatThrownBy(() -> lifecycle.apply(a, CANCEL, RECEPTIONIST, new TransitionContext(reason, NOW)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getCode()).isEqualTo("REASON_REQUIRED"));
        assertThat(a.getStatus()).isEqualTo(SCHEDULED);
    }

    @Test
    void cancelStoresReason() {
        Appointment a = appointment(SCHEDULED);
        lifecycle.apply(a, CANCEL, OWNER, new TransitionContext("  Trip  ", NOW));
        assertThat(a.getCancelReason()).isEqualTo("Trip");
    }

    @Test
    void expireRecordsSystemReason() {
        Appointment a = appointment(RESERVED);
        lifecycle.apply(a, EXPIRE, SYSTEM, new TransitionContext(null, NOW));
        assertThat(a.getStatus()).isEqualTo(CANCELLED);
        assertThat(a.getCancelReason()).isEqualTo("Reservation expired");
    }

    @Test
    void confirmAfterReservationExpiredIsRejected() {
        Appointment a = appointment(RESERVED);
        TransitionContext late = new TransitionContext(null, NOW.plusMinutes(11));
        assertThatThrownBy(() -> lifecycle.apply(a, CONFIRM, OWNER, late))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getCode()).isEqualTo("RESERVATION_EXPIRED"));
        assertThat(a.getStatus()).isEqualTo(RESERVED);
    }

    @Test
    void confirmExactlyAtReservedUntilIsRejected() {
        Appointment a = appointment(RESERVED);
        TransitionContext edge = new TransitionContext(null, NOW.plusMinutes(10));
        assertThatThrownBy(() -> lifecycle.apply(a, CONFIRM, OWNER, edge))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getCode()).isEqualTo("RESERVATION_EXPIRED"));
    }

    @Test
    void confirmJustBeforeReservedUntilSucceeds() {
        Appointment a = appointment(RESERVED);
        lifecycle.apply(a, CONFIRM, OWNER, new TransitionContext(null, NOW.plusMinutes(10).minusSeconds(1)));
        assertThat(a.getStatus()).isEqualTo(SCHEDULED);
    }

    @Test
    void observationsNotEditableWhenFinal() {
        assertThat(lifecycle.canEditObservations(CLOSED)).isFalse();
        assertThat(lifecycle.canEditObservations(CANCELLED)).isFalse();
        assertThat(lifecycle.canEditObservations(RESCHEDULED)).isFalse();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = AppointmentStatus.class,
            names = {"RESERVED", "SCHEDULED", "IN_PROGRESS", "FINISHED", "PAID"})
    void observationsEditableBeforeFinalStates(AppointmentStatus status) {
        assertThat(lifecycle.canEditObservations(status)).isTrue();
    }

    @Test
    void receptionistAllowedActionsOnScheduled() {
        assertThat(lifecycle.allowedActions(appointment(SCHEDULED), RECEPTIONIST))
                .containsExactlyInAnyOrder(START, CANCEL, RESCHEDULE, EDIT_OBSERVATIONS);
    }

    @Test
    void ownerAllowedActionsOnScheduled() {
        assertThat(lifecycle.allowedActions(appointment(SCHEDULED), OWNER)).containsExactly(CANCEL);
    }

    @Test
    void veterinarianAllowedActionsOnInProgress() {
        assertThat(lifecycle.allowedActions(appointment(IN_PROGRESS), VETERINARIAN))
                .containsExactlyInAnyOrder(FINISH, EDIT_OBSERVATIONS);
    }

    @Test
    void reservedAllowedActionsExcludeExpire() {
        assertThat(lifecycle.allowedActions(appointment(RESERVED), RECEPTIONIST))
                .containsExactlyInAnyOrder(CONFIRM, CANCEL, EDIT_OBSERVATIONS);
        assertThat(lifecycle.allowedActions(appointment(RESERVED), SYSTEM)).isEmpty();
    }

    @Test
    void finishedAndPaidAllowedActionsForReceptionist() {
        assertThat(lifecycle.allowedActions(appointment(FINISHED), RECEPTIONIST))
                .containsExactlyInAnyOrder(PAY, CLOSE, EDIT_OBSERVATIONS);
        assertThat(lifecycle.allowedActions(appointment(PAID), RECEPTIONIST))
                .containsExactlyInAnyOrder(CLOSE, EDIT_OBSERVATIONS);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = AppointmentStatus.class,
            names = {"CLOSED", "CANCELLED", "RESCHEDULED"})
    void finalStatesAllowNoActions(AppointmentStatus status) {
        for (Actor actor : Actor.values()) {
            assertThat(lifecycle.allowedActions(appointment(status), actor)).isEmpty();
        }
    }

    @Test
    void totalIsBasePricePlusVeterinarianCost() {
        assertThat(appointment(SCHEDULED).getTotal()).isEqualByComparingTo("55.00");
        assertThat(appointment(SCHEDULED).getEndAt()).isEqualTo(NOW.plusDays(1).plusMinutes(60));
    }
}
