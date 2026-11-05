package com.vetclinic.appointment;

import static com.vetclinic.appointment.Fixtures.NOW;
import static com.vetclinic.appointment.Fixtures.START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vetclinic.appointment.Fixtures.MutableClock;
import com.vetclinic.appointment.dto.AppointmentResponse;
import com.vetclinic.appointment.dto.RescheduleRequest;
import com.vetclinic.auth.CurrentUser;
import com.vetclinic.catalog.CatalogService;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.owner.Owner;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.PetService;
import com.vetclinic.payment.PaymentRepository;
import com.vetclinic.shared.error.DomainException;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;

class AppointmentActionServiceTests {

	AppointmentRepository appointments = mock(AppointmentRepository.class);
	PaymentRepository payments = mock(PaymentRepository.class);
	PetService petService = mock(PetService.class);
	CatalogService catalog = mock(CatalogService.class);
	VeterinarianRepository vets = mock(VeterinarianRepository.class);
	AvailabilityService availability = mock(AvailabilityService.class);
	MutableClock clock = new MutableClock(NOW);
	AppointmentLifecycle lifecycle = new AppointmentLifecycle();
	AppointmentQueryService queries = new AppointmentQueryService(appointments, payments, petService, lifecycle,
			clock);
	BookingService booking = new BookingService(petService, catalog, vets, appointments, availability, queries,
			clock, Duration.ofMinutes(10));
	AppointmentActionService actions = new AppointmentActionService(queries, appointments, lifecycle, booking, clock);

	Owner owner = Fixtures.owner(1L);
	Pet pet = Fixtures.pet(7L, owner);
	ClinicService grooming = Fixtures.service(2L);
	Veterinarian vet = Fixtures.vet(5L);
	Veterinarian otherVet = Fixtures.vet(6L);
	CurrentUser reception = Fixtures.receptionist();

	@BeforeEach
	void setUp() {
		when(appointments.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));
		when(appointments.saveAndFlush(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));
		when(vets.findLockedById(5L)).thenReturn(Optional.of(vet));
		when(vets.findLockedById(6L)).thenReturn(Optional.of(otherVet));
		when(availability.isBookable(any(), any(), any())).thenReturn(true);
	}

	private Appointment stored(Long id, AppointmentStatus status) {
		Appointment a = Fixtures.appointment(id, pet, grooming, vet, START, status);
		if (status == AppointmentStatus.RESERVED) {
			a.setReservedUntil(NOW.plusMinutes(5));
		}
		when(appointments.findById(id)).thenReturn(Optional.of(a));
		return a;
	}

	private void code(Runnable r, String code) {
		assertThatThrownBy(r::run).isInstanceOf(DomainException.class)
			.extracting(e -> ((DomainException) e).getCode())
			.isEqualTo(code);
	}

	@Test
	void ownerConfirmsOwnReservation() {
		Appointment a = stored(1L, AppointmentStatus.RESERVED);

		AppointmentResponse res = actions.confirm(Fixtures.ownerUser(1L), 1L);

		assertThat(a.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
		assertThat(res.status()).isEqualTo(AppointmentStatus.SCHEDULED);
	}

	@Test
	void confirmingExpiredReservationIsRejected() {
		stored(1L, AppointmentStatus.RESERVED);
		clock.set(NOW.plusMinutes(5));

		code(() -> actions.confirm(Fixtures.ownerUser(1L), 1L), "RESERVATION_EXPIRED");
	}

	@Test
	void otherOwnersAppointmentIsNotFound() {
		stored(1L, AppointmentStatus.RESERVED);

		code(() -> actions.confirm(Fixtures.ownerUser(2L), 1L), "APPOINTMENT_NOT_FOUND");
	}

	@Test
	void expireDueCancelsOnlyExpiredReservations() {
		Appointment expired = Fixtures.appointment(1L, pet, grooming, vet, START, AppointmentStatus.RESERVED);
		expired.setReservedUntil(NOW);
		when(appointments.findByStatusAndReservedUntilLessThanEqual(AppointmentStatus.RESERVED, NOW))
			.thenReturn(List.of(expired));
		Appointment live = Fixtures.appointment(2L, pet, grooming, vet, START, AppointmentStatus.RESERVED);
		live.setReservedUntil(NOW.plusMinutes(1));

		int count = actions.expireDue();

		assertThat(count).isEqualTo(1);
		assertThat(expired.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
		assertThat(expired.getCancelReason()).isEqualTo("Reservation expired");
		assertThat(live.getStatus()).isEqualTo(AppointmentStatus.RESERVED);
	}

	@Test
	void ownerCancelsOwnReservedAndScheduled() {
		Appointment reserved = stored(1L, AppointmentStatus.RESERVED);
		Appointment scheduled = stored(2L, AppointmentStatus.SCHEDULED);

		actions.cancel(Fixtures.ownerUser(1L), 1L, "changed my mind");
		actions.cancel(Fixtures.ownerUser(1L), 2L, "sick");

		assertThat(reserved.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
		assertThat(reserved.getCancelReason()).isEqualTo("changed my mind");
		assertThat(scheduled.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
	}

	@Test
	void receptionistCancelsReservedScheduledAndInProgress() {
		for (AppointmentStatus s : List.of(AppointmentStatus.RESERVED, AppointmentStatus.SCHEDULED,
				AppointmentStatus.IN_PROGRESS)) {
			Appointment a = stored(1L, s);
			actions.cancel(reception, 1L, "no show");
			assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
		}
	}

	@Test
	void cancelWithoutReasonIsRejected() {
		stored(1L, AppointmentStatus.SCHEDULED);

		code(() -> actions.cancel(reception, 1L, " "), "REASON_REQUIRED");
	}

	@Test
	void ownerCannotCancelInProgress() {
		stored(1L, AppointmentStatus.IN_PROGRESS);

		code(() -> actions.cancel(Fixtures.ownerUser(1L), 1L, "x"), "ACTION_NOT_ALLOWED");
	}

	@Test
	void receptionistCannotCancelFinished() {
		stored(1L, AppointmentStatus.FINISHED);

		code(() -> actions.cancel(reception, 1L, "x"), "INVALID_TRANSITION");
	}

	@Test
	void assignedVetStartsAndFinishes() {
		Appointment a = stored(1L, AppointmentStatus.SCHEDULED);

		actions.start(Fixtures.vetUser(5L), 1L);
		assertThat(a.getStatus()).isEqualTo(AppointmentStatus.IN_PROGRESS);
		actions.finish(Fixtures.vetUser(5L), 1L);
		assertThat(a.getStatus()).isEqualTo(AppointmentStatus.FINISHED);
	}

	@Test
	void receptionistStartsAndFinishesAny() {
		Appointment a = stored(1L, AppointmentStatus.SCHEDULED);

		actions.start(reception, 1L);
		actions.finish(reception, 1L);

		assertThat(a.getStatus()).isEqualTo(AppointmentStatus.FINISHED);
	}

	@Test
	void otherVetCannotStart() {
		stored(1L, AppointmentStatus.SCHEDULED);

		code(() -> actions.start(Fixtures.vetUser(6L), 1L), "APPOINTMENT_NOT_FOUND");
	}

	@Test
	void finishingScheduledIsRejected() {
		stored(1L, AppointmentStatus.SCHEDULED);

		code(() -> actions.finish(reception, 1L), "INVALID_TRANSITION");
	}

	@Test
	void observationsCanBeEditedUntilTheyLock() {
		for (AppointmentStatus s : List.of(AppointmentStatus.SCHEDULED, AppointmentStatus.IN_PROGRESS,
				AppointmentStatus.FINISHED, AppointmentStatus.PAID)) {
			Appointment a = stored(1L, s);
			actions.updateObservations(Fixtures.vetUser(5L), 1L, "note " + s);
			assertThat(a.getObservations()).isEqualTo("note " + s);
		}
	}

	@Test
	void observationsOnClosedAndCancelledAreLocked() {
		stored(1L, AppointmentStatus.CLOSED);
		stored(2L, AppointmentStatus.CANCELLED);

		code(() -> actions.updateObservations(reception, 1L, "x"), "OBSERVATIONS_LOCKED");
		code(() -> actions.updateObservations(reception, 2L, "x"), "OBSERVATIONS_LOCKED");
	}

	@Test
	void ownerCannotEditObservations() {
		stored(1L, AppointmentStatus.SCHEDULED);

		code(() -> actions.updateObservations(Fixtures.ownerUser(1L), 1L, "x"), "ACTION_NOT_ALLOWED");
	}

	@Test
	void receptionistReschedulesScheduledIntoFreeSlot() {
		Appointment original = stored(1L, AppointmentStatus.SCHEDULED);
		var newStart = START.plusDays(7);

		AppointmentResponse res = actions.reschedule(reception, 1L, new RescheduleRequest(5L, newStart));

		assertThat(original.getStatus()).isEqualTo(AppointmentStatus.RESCHEDULED);
		assertThat(res.status()).isEqualTo(AppointmentStatus.SCHEDULED);
		assertThat(res.startAt()).isEqualTo(newStart);
		assertThat(res.rescheduledFromId()).isEqualTo(1L);
		assertThat(res.pet().id()).isEqualTo(7L);
		assertThat(res.service().id()).isEqualTo(2L);
	}

	@Test
	void receptionistReassignsInProgressToAnotherVet() {
		Appointment original = stored(1L, AppointmentStatus.IN_PROGRESS);

		AppointmentResponse res = actions.reschedule(reception, 1L, new RescheduleRequest(6L, START));

		assertThat(original.getStatus()).isEqualTo(AppointmentStatus.RESCHEDULED);
		assertThat(res.veterinarian().id()).isEqualTo(6L);
	}

	@Test
	void rescheduleIntoOccupiedSlotKeepsTheOriginal() {
		Appointment original = stored(1L, AppointmentStatus.SCHEDULED);
		when(appointments.existsOverlap(any(), any(), any(), any(), any())).thenReturn(true);

		code(() -> actions.reschedule(reception, 1L, new RescheduleRequest(5L, START.plusDays(7))), "SLOT_TAKEN");

		assertThat(original.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
	}

	@Test
	void rescheduleFinishedIsRejected() {
		stored(1L, AppointmentStatus.FINISHED);

		code(() -> actions.reschedule(reception, 1L, new RescheduleRequest(5L, START.plusDays(7))),
				"INVALID_TRANSITION");
	}

	@Test
	void ownerCannotReschedule() {
		stored(1L, AppointmentStatus.SCHEDULED);

		code(() -> actions.reschedule(Fixtures.ownerUser(1L), 1L, new RescheduleRequest(5L, START.plusDays(7))),
				"ACTION_NOT_ALLOWED");
	}

	@Test
	void closeFromPaidAndFinished() {
		Appointment paid = stored(1L, AppointmentStatus.PAID);
		Appointment finished = stored(2L, AppointmentStatus.FINISHED);

		actions.close(reception, 1L);
		actions.close(reception, 2L);

		assertThat(paid.getStatus()).isEqualTo(AppointmentStatus.CLOSED);
		assertThat(finished.getStatus()).isEqualTo(AppointmentStatus.CLOSED);
	}

	@Test
	void closingClosedIsRejected() {
		stored(1L, AppointmentStatus.CLOSED);

		code(() -> actions.close(reception, 1L), "INVALID_TRANSITION");
	}
}
