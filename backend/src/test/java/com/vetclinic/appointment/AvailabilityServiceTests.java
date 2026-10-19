package com.vetclinic.appointment;

import static com.vetclinic.appointment.Fixtures.NOW;
import static com.vetclinic.appointment.Fixtures.T10;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vetclinic.appointment.dto.SlotResponse;
import com.vetclinic.catalog.CatalogService;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ServiceCode;
import com.vetclinic.owner.Owner;
import com.vetclinic.owner.Pet;
import com.vetclinic.shared.error.DomainException;
import com.vetclinic.shared.error.NotFoundException;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTests {

    static final LocalDate MONDAY = LocalDate.of(2026, 3, 2);

    @Mock CatalogService catalog;
    @Mock VeterinarianRepository veterinarians;
    @Mock AppointmentRepository appointments;

    MutableClock clock;
    AvailabilityService service;
    ClinicService consultation; // 30 min
    Veterinarian ana;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        service = new AvailabilityService(catalog, veterinarians, appointments, clock);
        consultation = Fixtures.consultation();
        ana = Fixtures.vet(100L, "Ana", "20.00");
    }

    private void stubDefaults() {
        when(catalog.require(1L)).thenReturn(consultation);
        when(veterinarians.findById(100L)).thenReturn(Optional.of(ana));
    }

    private Appointment blocking(LocalDateTime start, AppointmentStatus status) {
        Owner owner = Fixtures.owner(10L);
        Pet pet = Fixtures.pet(1L, owner);
        return Fixtures.appointment(9L, pet, consultation, ana, start, status,
                status == AppointmentStatus.RESERVED ? NOW.plusMinutes(10) : null);
    }

    @Test
    void slotsFollowFifteenMinuteGridInsideScheduleBlocks() {
        stubDefaults();

        List<SlotResponse> slots = service.slots(1L, Optional.of(100L), MONDAY);

        assertThat(slots).hasSize(30);
        assertThat(slots.get(0).startAt()).isEqualTo(LocalDateTime.of(2026, 3, 2, 9, 0));
        assertThat(slots.get(1).startAt()).isEqualTo(LocalDateTime.of(2026, 3, 2, 9, 15));
        assertThat(slots.get(0).endAt()).isEqualTo(LocalDateTime.of(2026, 3, 2, 9, 30));
        assertThat(slots).allSatisfy(s -> assertThat(s.veterinarianName()).isEqualTo("Ana"));
    }

    @Test
    void slotEndingExactlyAtBlockEndIsIncluded() {
        stubDefaults();

        List<SlotResponse> slots = service.slots(1L, Optional.of(100L), MONDAY);

        assertThat(slots).extracting(SlotResponse::endAt)
                .contains(LocalDateTime.of(2026, 3, 2, 13, 0), LocalDateTime.of(2026, 3, 2, 18, 0));
        assertThat(slots).extracting(SlotResponse::startAt)
                .doesNotContain(LocalDateTime.of(2026, 3, 2, 12, 45), LocalDateTime.of(2026, 3, 2, 13, 0));
    }

    @Test
    void withoutVeterinarianFilterEveryVeterinarianIsListed() {
        when(catalog.require(1L)).thenReturn(consultation);
        Veterinarian luis = Fixtures.vet(200L, "Luis", "30.00");
        when(veterinarians.findAll()).thenReturn(List.of(ana, luis));

        List<SlotResponse> slots = service.slots(1L, Optional.empty(), MONDAY);

        assertThat(slots).hasSize(60);
        assertThat(slots).extracting(SlotResponse::veterinarianId).containsOnly(100L, 200L);
    }

    @Test
    void blockingQueryReceivesCurrentTimeSoExpiredReservationsAreIgnored() {
        stubDefaults();

        service.slots(1L, Optional.of(100L), MONDAY);

        // The repository query frees expired RESERVED and CANCELLED/RESCHEDULED/FINISHED rows (AppointmentRepositoryTests)
        verify(appointments).findBlocking(eq(100L), eq(MONDAY.atStartOfDay()), eq(MONDAY.plusDays(1).atStartOfDay()),
                eq(NOW));
    }

    @Test
    void slotsOverlappingABlockingAppointmentAreExcluded() {
        stubDefaults();
        when(appointments.findBlocking(eq(100L), any(), any(), any()))
                .thenReturn(List.of(blocking(T10, AppointmentStatus.SCHEDULED)));

        List<SlotResponse> slots = service.slots(1L, Optional.of(100L), MONDAY);

        assertThat(slots).extracting(SlotResponse::startAt)
                .contains(LocalDateTime.of(2026, 3, 2, 9, 30), LocalDateTime.of(2026, 3, 2, 10, 30))
                .doesNotContain(LocalDateTime.of(2026, 3, 2, 9, 45), T10, LocalDateTime.of(2026, 3, 2, 10, 15));
    }

    @Test
    void inProgressAndLiveReservedAppointmentsAlsoBlock() {
        stubDefaults();
        when(appointments.findBlocking(eq(100L), any(), any(), any())).thenReturn(List.of(
                blocking(T10, AppointmentStatus.IN_PROGRESS),
                blocking(LocalDateTime.of(2026, 3, 2, 11, 0), AppointmentStatus.RESERVED)));

        List<SlotResponse> slots = service.slots(1L, Optional.of(100L), MONDAY);

        assertThat(slots).extracting(SlotResponse::startAt)
                .doesNotContain(T10, LocalDateTime.of(2026, 3, 2, 11, 0));
    }

    @Test
    void slotsAtOrBeforeNowAreExcluded() {
        stubDefaults();
        clock.advance(Duration.ofHours(2)); // 10:00

        List<SlotResponse> slots = service.slots(1L, Optional.of(100L), MONDAY);

        assertThat(slots.get(0).startAt()).isEqualTo(LocalDateTime.of(2026, 3, 2, 10, 15));
    }

    @Test
    void dayWithoutScheduleBlocksHasNoSlots() {
        stubDefaults();

        assertThat(service.slots(1L, Optional.of(100L), LocalDate.of(2026, 3, 7))).isEmpty(); // Saturday
    }

    @Test
    void serviceLongerThanEveryBlockHasNoSlots() {
        ClinicService hospitalization = Fixtures.service(4L, ServiceCode.HOSPITALIZATION, 300, "150.00");
        when(catalog.require(4L)).thenReturn(hospitalization);
        when(veterinarians.findById(100L)).thenReturn(Optional.of(ana));

        assertThat(service.slots(4L, Optional.of(100L), MONDAY)).isEmpty();
    }

    @Test
    void pastDateHasNoSlots() {
        stubDefaults();

        assertThat(service.slots(1L, Optional.of(100L), MONDAY.minusDays(7))).isEmpty();
    }

    @Test
    void unknownServiceIsNotFound() {
        when(catalog.require(99L)).thenThrow(new NotFoundException("SERVICE_NOT_FOUND", "Service 99 not found"));

        assertThatThrownBy(() -> service.slots(99L, Optional.empty(), MONDAY))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo("SERVICE_NOT_FOUND"));
    }

    @Test
    void unknownVeterinarianIsNotFound() {
        when(catalog.require(1L)).thenReturn(consultation);
        when(veterinarians.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.slots(1L, Optional.of(99L), MONDAY))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo("VETERINARIAN_NOT_FOUND"));
    }

    @Test
    void isBookableAcceptsGridAlignedFutureStartInsideBlock() {
        assertThat(service.isBookable(ana, consultation, T10)).isTrue();
        assertThat(service.isBookable(ana, consultation, LocalDateTime.of(2026, 3, 2, 12, 30))).isTrue();
    }

    @Test
    void isBookableRejectsOffGridPastOutsideBlockAndOverrun() {
        assertThat(service.isBookable(ana, consultation, T10.plusMinutes(5))).isFalse();
        assertThat(service.isBookable(ana, consultation, NOW)).isFalse();
        assertThat(service.isBookable(ana, consultation, LocalDateTime.of(2026, 3, 2, 13, 30))).isFalse();
        assertThat(service.isBookable(ana, consultation, LocalDateTime.of(2026, 3, 2, 12, 45))).isFalse();
        assertThat(service.isBookable(ana, consultation, LocalDateTime.of(2026, 3, 7, 10, 0))).isFalse();
    }
}
