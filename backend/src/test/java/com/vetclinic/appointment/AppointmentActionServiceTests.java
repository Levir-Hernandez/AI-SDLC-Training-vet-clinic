package com.vetclinic.appointment;

import static com.vetclinic.appointment.Fixtures.NOW;
import static com.vetclinic.appointment.Fixtures.OWNER_A;
import static com.vetclinic.appointment.Fixtures.OWNER_B;
import static com.vetclinic.appointment.Fixtures.RECEPTIONIST;
import static com.vetclinic.appointment.Fixtures.T10;
import static com.vetclinic.appointment.Fixtures.VET_ANA;
import static com.vetclinic.appointment.Fixtures.VET_LUIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vetclinic.appointment.dto.AppointmentResponse;
import com.vetclinic.appointment.dto.RescheduleRequest;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.owner.Pet;
import com.vetclinic.payment.PaymentRepository;
import com.vetclinic.shared.error.ConflictException;
import com.vetclinic.shared.error.DomainException;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppointmentActionServiceTests {

    static final LocalDateTime NEW_START = LocalDateTime.of(2026, 3, 3, 11, 0);

    @Mock AppointmentRepository appointments;
    @Mock VeterinarianRepository veterinarians;
    @Mock PaymentRepository payments;
    @Mock BookingService booking;

    MutableClock clock;
    AppointmentActionService actions;
    Pet pet;
    ClinicService consultation;
    Veterinarian ana;
    Veterinarian luis;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        AppointmentQueryService queries = new AppointmentQueryService(appointments, payments, null,
                new AppointmentLifecycle(), clock);
        actions = new AppointmentActionService(appointments, veterinarians, queries, booking,
                new AppointmentLifecycle(), clock);
        pet = Fixtures.pet(1L, Fixtures.owner(10L));
        consultation = Fixtures.consultation();
        ana = Fixtures.vet(100L, "Ana", "20.00");
        luis = Fixtures.vet(200L, "Luis", "30.00");
    }

    private Appointment stored(AppointmentStatus status) {
        LocalDateTime reservedUntil = status == AppointmentStatus.RESERVED ? NOW.plusMinutes(10) : null;
        Appointment a = Fixtures.appointment(1L, pet, consultation, ana, T10, status, reservedUntil);
        when(appointments.findById(1L)).thenReturn(Optional.of(a));
        return a;
    }

    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }

    // ---- confirm / expire

    @Test
    void ownerConfirmsReservationBeforeItExpires() {
        Appointment a = stored(AppointmentStatus.RESERVED);
        clock.advance(Duration.ofMinutes(9));

        AppointmentResponse response = actions.confirm(OWNER_A, 1L);

        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(response.status()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void confirmAtReservedUntilIsRejectedAsExpired() {
        Appointment a = stored(AppointmentStatus.RESERVED);
        clock.advance(Duration.ofMinutes(10));

        assertCode(() -> actions.confirm(OWNER_A, 1L), "RESERVATION_EXPIRED");
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.RESERVED);
    }

    @Test
    void confirmAfterReservedUntilIsRejectedAsExpired() {
        stored(AppointmentStatus.RESERVED);
        clock.advance(Duration.ofMinutes(11));

        assertCode(() -> actions.confirm(RECEPTIONIST, 1L), "RESERVATION_EXPIRED");
    }

    @Test
    void expireDueCancelsTheExpiredReservationsReturnedByTheQuery() {
        Appointment expired = Fixtures.appointment(1L, pet, consultation, ana, T10, AppointmentStatus.RESERVED,
                NOW.minusMinutes(1));
        clock.advance(Duration.ofMinutes(1)); // NOW + 1m; the query is bounded by the current time
        when(appointments.findByStatusAndReservedUntilLessThanEqual(AppointmentStatus.RESERVED, NOW.plusMinutes(1)))
                .thenReturn(List.of(expired));

        int count = actions.expireDue();

        assertThat(count).isEqualTo(1);
        assertThat(expired.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(expired.getCancelReason()).isEqualTo("Reservation expired");
    }

    @Test
    void expireDueWithNothingDueChangesNothing() {
        assertThat(actions.expireDue()).isZero();
    }

    // ---- cancel

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"RESERVED", "SCHEDULED"})
    void ownerCancelsOwnReservedOrScheduledAppointment(AppointmentStatus status) {
        Appointment a = stored(status);

        AppointmentResponse response = actions.cancel(OWNER_A, 1L, "  Travelling ");

        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(response.cancelReason()).isEqualTo("Travelling");
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"RESERVED", "SCHEDULED", "IN_PROGRESS"})
    void receptionistCancelsReservedScheduledOrInProgress(AppointmentStatus status) {
        Appointment a = stored(status);

        actions.cancel(RECEPTIONIST, 1L, "Owner called");

        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void cancelWithBlankReasonIsRejected() {
        Appointment a = stored(AppointmentStatus.SCHEDULED);

        assertCode(() -> actions.cancel(RECEPTIONIST, 1L, "   "), "REASON_REQUIRED");
        assertCode(() -> actions.cancel(RECEPTIONIST, 1L, null), "REASON_REQUIRED");
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void ownerCannotCancelAnInProgressAppointment() {
        stored(AppointmentStatus.IN_PROGRESS);

        assertCode(() -> actions.cancel(OWNER_A, 1L, "Changed my mind"), "ACTION_NOT_ALLOWED");
    }

    @Test
    void receptionistCannotCancelAFinishedAppointment() {
        stored(AppointmentStatus.FINISHED);

        assertCode(() -> actions.cancel(RECEPTIONIST, 1L, "Mistake"), "INVALID_TRANSITION");
    }

    @Test
    void ownerCannotActOnAnotherOwnersAppointment() {
        Appointment a = stored(AppointmentStatus.SCHEDULED);

        assertCode(() -> actions.cancel(OWNER_B, 1L, "Not mine"), "APPOINTMENT_NOT_FOUND");
        assertCode(() -> actions.confirm(OWNER_B, 1L), "APPOINTMENT_NOT_FOUND");
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void unknownAppointmentIsNotFound() {
        when(appointments.findById(99L)).thenReturn(Optional.empty());

        assertCode(() -> actions.start(RECEPTIONIST, 99L), "APPOINTMENT_NOT_FOUND");
    }

    // ---- start / finish

    @Test
    void assignedVeterinarianStartsAndFinishesOwnAppointment() {
        Appointment a = stored(AppointmentStatus.SCHEDULED);

        actions.start(VET_ANA, 1L);
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.IN_PROGRESS);

        actions.finish(VET_ANA, 1L);
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.FINISHED);
    }

    @Test
    void receptionistStartsAndFinishesAnyAppointment() {
        Appointment a = stored(AppointmentStatus.SCHEDULED);

        actions.start(RECEPTIONIST, 1L);
        actions.finish(RECEPTIONIST, 1L);

        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.FINISHED);
    }

    @Test
    void otherVeterinarianCannotStartAnAppointment() {
        Appointment a = stored(AppointmentStatus.SCHEDULED);

        assertCode(() -> actions.start(VET_LUIS, 1L), "APPOINTMENT_NOT_FOUND");
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void finishingAScheduledAppointmentIsRejected() {
        stored(AppointmentStatus.SCHEDULED);

        assertCode(() -> actions.finish(VET_ANA, 1L), "INVALID_TRANSITION");
    }

    // ---- observations

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"SCHEDULED", "IN_PROGRESS", "FINISHED", "PAID"})
    void observationsAreSavedWhileTheAppointmentIsOpen(AppointmentStatus status) {
        Appointment a = stored(status);

        AppointmentResponse response = actions.updateObservations(VET_ANA, 1L, "Healthy");

        assertThat(a.getObservations()).isEqualTo("Healthy");
        assertThat(response.observations()).isEqualTo("Healthy");
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"CLOSED", "CANCELLED", "RESCHEDULED"})
    void observationsOnFinalAppointmentsAreLocked(AppointmentStatus status) {
        Appointment a = stored(status);

        assertCode(() -> actions.updateObservations(RECEPTIONIST, 1L, "Late note"), "OBSERVATIONS_LOCKED");
        assertThat(a.getObservations()).isNull();
    }

    @Test
    void ownersCannotEditObservations() {
        assertCode(() -> actions.updateObservations(OWNER_A, 1L, "Note"), "ACTION_NOT_ALLOWED");
    }

    @Test
    void otherVeterinarianCannotEditObservations() {
        stored(AppointmentStatus.SCHEDULED);

        assertCode(() -> actions.updateObservations(VET_LUIS, 1L, "Note"), "APPOINTMENT_NOT_FOUND");
    }

    // ---- reschedule / reassign

    private Appointment newAppointment(Veterinarian vet, Appointment from) {
        return Fixtures.withId(new Appointment(pet, consultation, vet, NEW_START, AppointmentStatus.SCHEDULED,
                Actor.RECEPTIONIST, null, from, NOW), 2L);
    }

    @Test
    void scheduledAppointmentIsRescheduledToANewTime() {
        Appointment original = stored(AppointmentStatus.SCHEDULED);
        when(booking.createScheduled(eq(pet), eq(consultation), eq(ana), eq(NEW_START), eq(Actor.RECEPTIONIST),
                eq(original), eq(1L))).thenReturn(newAppointment(ana, original));

        AppointmentResponse response = actions.reschedule(RECEPTIONIST, 1L, new RescheduleRequest(null, NEW_START));

        assertThat(original.getStatus()).isEqualTo(AppointmentStatus.RESCHEDULED);
        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.status()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(response.rescheduledFromId()).isEqualTo(1L);
        assertThat(response.startAt()).isEqualTo(NEW_START);
    }

    @Test
    void inProgressAppointmentIsReassignedToAnotherVeterinarian() {
        Appointment original = stored(AppointmentStatus.IN_PROGRESS);
        when(veterinarians.findById(200L)).thenReturn(Optional.of(luis));
        when(booking.createScheduled(eq(pet), eq(consultation), eq(luis), eq(NEW_START), eq(Actor.RECEPTIONIST),
                eq(original), eq(1L))).thenReturn(newAppointment(luis, original));

        AppointmentResponse response = actions.reschedule(RECEPTIONIST, 1L, new RescheduleRequest(200L, NEW_START));

        assertThat(original.getStatus()).isEqualTo(AppointmentStatus.RESCHEDULED);
        assertThat(response.veterinarian().id()).isEqualTo(200L);
        assertThat(response.total()).isEqualByComparingTo("70.00");
    }

    @Test
    void rescheduleIntoAnOccupiedSlotKeepsTheOriginalScheduled() {
        Appointment original = stored(AppointmentStatus.SCHEDULED);
        when(booking.createScheduled(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new ConflictException("SLOT_TAKEN", "That time slot is already taken"));

        assertCode(() -> actions.reschedule(RECEPTIONIST, 1L, new RescheduleRequest(null, NEW_START)), "SLOT_TAKEN");
        assertThat(original.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void finishedAppointmentCannotBeRescheduled() {
        stored(AppointmentStatus.FINISHED);

        assertCode(() -> actions.reschedule(RECEPTIONIST, 1L, new RescheduleRequest(null, NEW_START)),
                "INVALID_TRANSITION");
        verifyNoBooking();
    }

    @Test
    void ownerCannotReschedule() {
        Appointment original = stored(AppointmentStatus.SCHEDULED);

        assertCode(() -> actions.reschedule(OWNER_A, 1L, new RescheduleRequest(null, NEW_START)),
                "ACTION_NOT_ALLOWED");
        assertThat(original.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        verifyNoBooking();
    }

    private void verifyNoBooking() {
        org.mockito.Mockito.verifyNoInteractions(booking);
    }

    // ---- close

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"FINISHED", "PAID"})
    void receptionistClosesFinishedOrPaidAppointment(AppointmentStatus status) {
        Appointment a = stored(status);

        actions.close(RECEPTIONIST, 1L);

        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CLOSED);
    }

    @Test
    void closingAClosedAppointmentIsRejected() {
        stored(AppointmentStatus.CLOSED);

        assertCode(() -> actions.close(RECEPTIONIST, 1L), "INVALID_TRANSITION");
    }

    @Test
    void veterinarianCannotClose() {
        stored(AppointmentStatus.FINISHED);

        assertCode(() -> actions.close(VET_ANA, 1L), "ACTION_NOT_ALLOWED");
        verify(appointments).findById(1L);
    }
}
