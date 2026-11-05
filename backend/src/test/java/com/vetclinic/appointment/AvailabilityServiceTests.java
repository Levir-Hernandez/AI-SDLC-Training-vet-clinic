package com.vetclinic.appointment;

import static com.vetclinic.appointment.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vetclinic.appointment.Fixtures.MutableClock;
import com.vetclinic.appointment.dto.SlotResponse;
import com.vetclinic.catalog.CatalogService;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ServiceCode;
import com.vetclinic.shared.error.DomainException;
import com.vetclinic.shared.error.NotFoundException;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;
import com.vetclinic.veterinarian.VeterinarianService;

class AvailabilityServiceTests {

	static final LocalDate WEDNESDAY = LocalDate.of(2030, 1, 2);

	CatalogService catalog = mock(CatalogService.class);
	VeterinarianService vetService = mock(VeterinarianService.class);
	VeterinarianRepository vets = mock(VeterinarianRepository.class);
	AppointmentRepository appointments = mock(AppointmentRepository.class);
	MutableClock clock = new MutableClock(NOW);
	AvailabilityService service = new AvailabilityService(catalog, vetService, vets, appointments, clock);

	ClinicService grooming = Fixtures.service(1L);
	Veterinarian vet = Fixtures.vet(5L);

	@BeforeEach
	void setUp() {
		when(catalog.require(1L)).thenReturn(grooming);
		when(vets.findAll()).thenReturn(List.of(vet));
		when(vetService.require(5L)).thenReturn(vet);
	}

	private Appointment blocker(LocalDateTime start, AppointmentStatus status) {
		return Fixtures.appointment(50L, Fixtures.pet(1L, Fixtures.owner(1L)), grooming, vet, start, status);
	}

	@Test
	void slotsFollowTheFifteenMinuteGridAndIncludeTheOneEndingAtBlockEnd() {
		List<SlotResponse> slots = service.slots(1L, Optional.empty(), WEDNESDAY);

		assertThat(slots).hasSize(29);
		assertThat(slots.get(0).startAt()).isEqualTo(WEDNESDAY.atTime(9, 0));
		assertThat(slots.get(1).startAt()).isEqualTo(WEDNESDAY.atTime(9, 15));
		assertThat(slots.get(28).startAt()).isEqualTo(WEDNESDAY.atTime(16, 0));
		assertThat(slots.get(28).endAt()).isEqualTo(WEDNESDAY.atTime(17, 0));
		assertThat(slots.get(0).veterinarianId()).isEqualTo(5L);
	}

	@Test
	void queriesBlockingAppointmentsAtTheClockTime() {
		service.slots(1L, Optional.of(5L), WEDNESDAY);

		verify(appointments).findBlocking(5L, WEDNESDAY.atStartOfDay(), WEDNESDAY.plusDays(1).atStartOfDay(), NOW);
	}

	@Test
	void nonBlockingAppointmentsLeaveEveryoneFree() {
		when(appointments.findBlocking(eq(5L), any(), any(), any())).thenReturn(List.of());

		assertThat(service.slots(1L, Optional.empty(), WEDNESDAY)).hasSize(29);
	}

	@Test
	void overlappingScheduledAppointmentRemovesOverlappingSlots() {
		when(appointments.findBlocking(eq(5L), any(), any(), any()))
			.thenReturn(List.of(blocker(WEDNESDAY.atTime(10, 0), AppointmentStatus.SCHEDULED)));

		List<LocalDateTime> starts = service.slots(1L, Optional.empty(), WEDNESDAY).stream().map(SlotResponse::startAt).toList();

		assertThat(starts).contains(WEDNESDAY.atTime(9, 0), WEDNESDAY.atTime(11, 0));
		assertThat(starts).doesNotContain(WEDNESDAY.atTime(9, 15), WEDNESDAY.atTime(10, 0),
				WEDNESDAY.atTime(10, 45));
	}

	@Test
	void inProgressAndLiveReservedAlsoBlock() {
		when(appointments.findBlocking(eq(5L), any(), any(), any())).thenReturn(
				List.of(blocker(WEDNESDAY.atTime(9, 0), AppointmentStatus.IN_PROGRESS),
						blocker(WEDNESDAY.atTime(12, 0), AppointmentStatus.RESERVED)));

		List<LocalDateTime> starts = service.slots(1L, Optional.empty(), WEDNESDAY).stream().map(SlotResponse::startAt).toList();

		assertThat(starts).doesNotContain(WEDNESDAY.atTime(9, 0), WEDNESDAY.atTime(12, 0));
		assertThat(starts).contains(WEDNESDAY.atTime(10, 0), WEDNESDAY.atTime(13, 0));
	}

	@Test
	void slotsAtOrBeforeNowAreExcluded() {
		clock.set(WEDNESDAY.atTime(10, 0));

		List<LocalDateTime> starts = service.slots(1L, Optional.empty(), WEDNESDAY).stream().map(SlotResponse::startAt).toList();

		assertThat(starts.get(0)).isEqualTo(WEDNESDAY.atTime(10, 15));
	}

	@Test
	void pastDateHasNoSlots() {
		clock.set(WEDNESDAY.plusDays(1).atTime(8, 0));

		assertThat(service.slots(1L, Optional.empty(), WEDNESDAY)).isEmpty();
	}

	@Test
	void dayWithoutScheduleBlocksHasNoSlots() {
		assertThat(service.slots(1L, Optional.empty(), WEDNESDAY.plusDays(1))).isEmpty();
	}

	@Test
	void serviceLongerThanTheBlockHasNoSlots() {
		ClinicService surgery = Fixtures.withId(
				new ClinicService(ServiceCode.GROOMING, "Long", 600, new BigDecimal("10.00")), 2L);
		when(catalog.require(2L)).thenReturn(surgery);

		assertThat(service.slots(2L, Optional.empty(), WEDNESDAY)).isEmpty();
	}

	@Test
	void unknownServiceIsNotFound() {
		when(catalog.require(9L)).thenThrow(new NotFoundException("SERVICE_NOT_FOUND", "no"));

		assertThatThrownBy(() -> service.slots(9L, Optional.empty(), WEDNESDAY)).isInstanceOf(DomainException.class)
			.extracting(e -> ((DomainException) e).getCode())
			.isEqualTo("SERVICE_NOT_FOUND");
	}

	@Test
	void unknownVeterinarianIsNotFound() {
		when(vetService.require(9L)).thenThrow(new NotFoundException("VETERINARIAN_NOT_FOUND", "no"));

		assertThatThrownBy(() -> service.slots(1L, Optional.of(9L), WEDNESDAY)).isInstanceOf(DomainException.class)
			.extracting(e -> ((DomainException) e).getCode())
			.isEqualTo("VETERINARIAN_NOT_FOUND");
	}

	@Test
	void gridAlignedFutureSlotInsideBlockIsBookable() {
		assertThat(service.isBookable(vet, grooming, WEDNESDAY.atTime(16, 0))).isTrue();
	}

	@Test
	void offGridStartIsNotBookable() {
		assertThat(service.isBookable(vet, grooming, WEDNESDAY.atTime(10, 10))).isFalse();
	}

	@Test
	void slotRunningPastBlockEndIsNotBookable() {
		assertThat(service.isBookable(vet, grooming, WEDNESDAY.atTime(16, 15))).isFalse();
	}

	@Test
	void startOnADayWithoutBlockIsNotBookable() {
		assertThat(service.isBookable(vet, grooming, WEDNESDAY.plusDays(1).atTime(10, 0))).isFalse();
	}

	@Test
	void pastStartIsNotBookable() {
		clock.set(WEDNESDAY.atTime(12, 0));

		assertThat(service.isBookable(vet, grooming, WEDNESDAY.atTime(10, 0))).isFalse();
	}
}
