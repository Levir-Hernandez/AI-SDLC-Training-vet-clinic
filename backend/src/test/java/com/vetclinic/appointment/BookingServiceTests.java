package com.vetclinic.appointment;

import static com.vetclinic.appointment.Fixtures.NOW;
import static com.vetclinic.appointment.Fixtures.START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.vetclinic.appointment.Fixtures.MutableClock;
import com.vetclinic.appointment.dto.CreateAppointmentRequest;
import com.vetclinic.catalog.CatalogService;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.owner.Owner;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.PetService;
import com.vetclinic.shared.error.DomainException;
import com.vetclinic.shared.error.NotFoundException;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;

class BookingServiceTests {

	PetService petService = mock(PetService.class);
	CatalogService catalog = mock(CatalogService.class);
	VeterinarianRepository vets = mock(VeterinarianRepository.class);
	AppointmentRepository appointments = mock(AppointmentRepository.class);
	AvailabilityService availability = mock(AvailabilityService.class);
	AppointmentQueryService queries = mock(AppointmentQueryService.class);
	MutableClock clock = new MutableClock(NOW);
	BookingService booking = new BookingService(petService, catalog, vets, appointments, availability, queries, clock,
			Duration.ofMinutes(10));

	Owner owner = Fixtures.owner(1L);
	Pet pet = Fixtures.pet(7L, owner);
	ClinicService grooming = Fixtures.service(2L);
	Veterinarian vet = Fixtures.vet(5L);
	CreateAppointmentRequest req = new CreateAppointmentRequest(7L, 2L, 5L, START);

	@BeforeEach
	void setUp() {
		when(petService.requireAccessible(any(), eq(7L))).thenReturn(pet);
		when(catalog.require(2L)).thenReturn(grooming);
		when(vets.findLockedById(5L)).thenReturn(Optional.of(vet));
		when(availability.isBookable(vet, grooming, START)).thenReturn(true);
		when(appointments.saveAndFlush(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));
	}

	private Appointment saved() {
		ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
		verify(appointments).saveAndFlush(captor.capture());
		return captor.getValue();
	}

	private void code(Runnable r, String code) {
		assertThatThrownBy(r::run).isInstanceOf(DomainException.class)
			.extracting(e -> ((DomainException) e).getCode())
			.isEqualTo(code);
	}

	@Test
	void receptionistWalkInIsScheduledWithFixedTotal() {
		booking.book(Fixtures.receptionist(), req);

		Appointment a = saved();
		assertThat(a.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
		assertThat(a.getBookedBy()).isEqualTo(Actor.RECEPTIONIST);
		assertThat(a.getTotal()).isEqualByComparingTo(new BigDecimal("60.00"));
		assertThat(a.getTotal().scale()).isEqualTo(2);
		assertThat(a.getEndAt()).isEqualTo(START.plusMinutes(60));
		assertThat(a.getReservedUntil()).isNull();
	}

	@Test
	void ownerBookingIsReservedForTheConfiguredTime() {
		booking.book(Fixtures.ownerUser(1L), req);

		Appointment a = saved();
		assertThat(a.getStatus()).isEqualTo(AppointmentStatus.RESERVED);
		assertThat(a.getBookedBy()).isEqualTo(Actor.OWNER);
		assertThat(a.getReservedUntil()).isEqualTo(NOW.plusMinutes(10));
	}

	@Test
	void takenSlotIsRejected() {
		when(appointments.existsOverlap(eq(5L), eq(START), eq(START.plusMinutes(60)), eq(NOW), any())).thenReturn(true);

		code(() -> booking.book(Fixtures.receptionist(), req), "SLOT_TAKEN");
	}

	@Test
	void startInThePastIsRejected() {
		clock.set(START.plusMinutes(1));

		code(() -> booking.book(Fixtures.receptionist(), req), "START_IN_PAST");
	}

	@Test
	void startEqualToNowIsRejected() {
		clock.set(START);

		code(() -> booking.book(Fixtures.receptionist(), req), "START_IN_PAST");
	}

	@Test
	void slotOutsideScheduleIsUnavailable() {
		when(availability.isBookable(vet, grooming, START)).thenReturn(false);

		code(() -> booking.book(Fixtures.receptionist(), req), "SLOT_UNAVAILABLE");
	}

	@Test
	void otherOwnersPetIsNotFound() {
		when(petService.requireAccessible(any(), eq(7L))).thenThrow(new NotFoundException("PET_NOT_FOUND", "no"));

		code(() -> booking.book(Fixtures.ownerUser(2L), req), "PET_NOT_FOUND");
	}

	@Test
	void veterinarianCannotBook() {
		code(() -> booking.book(Fixtures.vetUser(5L), req), "ACTION_NOT_ALLOWED");
	}

	@Test
	void unknownVeterinarianIsNotFound() {
		when(vets.findLockedById(anyLong())).thenReturn(Optional.empty());

		code(() -> booking.book(Fixtures.receptionist(), req), "VETERINARIAN_NOT_FOUND");
	}

	@Test
	void rescheduleBookingExcludesTheOriginalAndLinksIt() {
		Appointment original = Fixtures.appointment(40L, pet, grooming, vet, START.plusDays(7),
				AppointmentStatus.SCHEDULED);

		Appointment created = booking.createScheduled(pet, grooming, 5L, START, Actor.RECEPTIONIST, original, 40L);

		assertThat(created.getRescheduledFrom()).isSameAs(original);
		verify(appointments).existsOverlap(5L, START, START.plusMinutes(60), NOW, 40L);
	}
}
