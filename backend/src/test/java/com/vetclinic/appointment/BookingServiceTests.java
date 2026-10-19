package com.vetclinic.appointment;

import static com.vetclinic.appointment.Fixtures.NOW;
import static com.vetclinic.appointment.Fixtures.OWNER_A;
import static com.vetclinic.appointment.Fixtures.OWNER_B;
import static com.vetclinic.appointment.Fixtures.RECEPTIONIST;
import static com.vetclinic.appointment.Fixtures.T10;
import static com.vetclinic.appointment.Fixtures.VET_ANA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vetclinic.appointment.dto.AppointmentResponse;
import com.vetclinic.appointment.dto.CreateAppointmentRequest;
import com.vetclinic.auth.CurrentUser;
import com.vetclinic.catalog.CatalogService;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.owner.Owner;
import com.vetclinic.owner.OwnerRepository;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.PetRepository;
import com.vetclinic.owner.PetService;
import com.vetclinic.payment.PaymentRepository;
import com.vetclinic.shared.error.DomainException;
import com.vetclinic.shared.error.ForbiddenException;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingServiceTests {

    @Mock PetRepository pets;
    @Mock OwnerRepository owners;
    @Mock CatalogService catalog;
    @Mock VeterinarianRepository veterinarians;
    @Mock AppointmentRepository appointments;
    @Mock PaymentRepository payments;

    MutableClock clock;
    BookingService booking;
    Pet petOfA;
    ClinicService consultation; // 30 min, 40.00
    Veterinarian ana;           // cost 20.00

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        PetService petService = new PetService(pets, owners);
        AvailabilityService availability = new AvailabilityService(catalog, veterinarians, appointments, clock);
        AppointmentQueryService queries = new AppointmentQueryService(appointments, payments, petService,
                new AppointmentLifecycle(), clock);
        booking = new BookingService(petService, catalog, veterinarians, appointments, availability, queries, clock,
                Duration.ofMinutes(10));
        Owner ownerA = Fixtures.owner(10L);
        petOfA = Fixtures.pet(1L, ownerA);
        consultation = Fixtures.consultation();
        ana = Fixtures.vet(100L, "Ana", "20.00");
    }

    private void stubBookingData() {
        when(pets.findById(1L)).thenReturn(Optional.of(petOfA));
        when(catalog.require(1L)).thenReturn(consultation);
        when(veterinarians.findById(100L)).thenReturn(Optional.of(ana));
        when(veterinarians.findLockedById(100L)).thenReturn(Optional.of(ana));
    }

    private void stubSave() {
        when(appointments.save(any(Appointment.class))).thenAnswer(inv -> Fixtures.withId(inv.getArgument(0), 50L));
    }

    private CreateAppointmentRequest request(LocalDateTime start) {
        return new CreateAppointmentRequest(1L, 1L, 100L, start);
    }

    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test
    void receptionistBooksWalkInAsScheduledWithFixedTotal() {
        stubBookingData();
        stubSave();

        AppointmentResponse response = booking.book(RECEPTIONIST, request(T10));

        assertThat(response.status()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(response.bookedBy()).isEqualTo(Actor.RECEPTIONIST);
        assertThat(response.total()).isEqualByComparingTo(new BigDecimal("60.00"));
        assertThat(response.total().scale()).isEqualTo(2);
        assertThat(response.reservedUntil()).isNull();
        assertThat(response.endAt()).isEqualTo(T10.plusMinutes(30));
    }

    @Test
    void ownerReservesOwnPetWithExpiryFromTtl() {
        stubBookingData();
        stubSave();

        AppointmentResponse response = booking.book(OWNER_A, request(T10));

        assertThat(response.status()).isEqualTo(AppointmentStatus.RESERVED);
        assertThat(response.bookedBy()).isEqualTo(Actor.OWNER);
        assertThat(response.reservedUntil()).isEqualTo(NOW.plusMinutes(10));
    }

    @Test
    void bookingOverlappingAnotherAppointmentIsRejectedAsSlotTaken() {
        stubBookingData();
        when(appointments.existsOverlap(eq(100L), eq(T10), eq(T10.plusMinutes(30)), eq(NOW), any())).thenReturn(true);

        assertCode(() -> booking.book(RECEPTIONIST, request(T10)), "SLOT_TAKEN");
        verify(appointments, never()).save(any());
    }

    @Test
    void bookingLocksTheVeterinarianBeforeCheckingOverlap() {
        stubBookingData();
        stubSave();

        booking.book(RECEPTIONIST, request(T10));

        verify(veterinarians).findLockedById(100L);
    }

    @Test
    void bookingInThePastIsRejected() {
        when(pets.findById(1L)).thenReturn(Optional.of(petOfA));
        when(catalog.require(1L)).thenReturn(consultation);
        when(veterinarians.findById(100L)).thenReturn(Optional.of(ana));

        assertCode(() -> booking.book(RECEPTIONIST, request(NOW)), "START_IN_PAST");
        assertCode(() -> booking.book(RECEPTIONIST, request(NOW.minusDays(1))), "START_IN_PAST");
        verify(appointments, never()).save(any());
    }

    @Test
    void bookingOutsideTheScheduleIsRejectedAsSlotUnavailable() {
        stubBookingData();

        assertCode(() -> booking.book(RECEPTIONIST, request(LocalDateTime.of(2026, 3, 2, 20, 0))), "SLOT_UNAVAILABLE");
        assertCode(() -> booking.book(RECEPTIONIST, request(LocalDateTime.of(2026, 3, 7, 10, 0))), "SLOT_UNAVAILABLE");
        assertCode(() -> booking.book(RECEPTIONIST, request(T10.plusMinutes(5))), "SLOT_UNAVAILABLE");
        verify(appointments, never()).save(any());
    }

    @Test
    void ownerCannotBookForAnotherOwnersPet() {
        when(pets.findById(1L)).thenReturn(Optional.of(petOfA));

        assertCode(() -> booking.book(OWNER_B, request(T10)), "PET_NOT_FOUND");
        verify(appointments, never()).save(any());
    }

    @Test
    void veterinarianCannotBook() {
        assertThatThrownBy(() -> booking.book(VET_ANA, request(T10))).isInstanceOf(ForbiddenException.class);
        verify(appointments, never()).save(any());
    }

    @Test
    void unknownPetIsNotFound() {
        when(pets.findById(anyLong())).thenReturn(Optional.empty());
        CurrentUser receptionist = RECEPTIONIST;

        assertCode(() -> booking.book(receptionist, new CreateAppointmentRequest(9L, 1L, 100L, T10)), "PET_NOT_FOUND");
    }
}
