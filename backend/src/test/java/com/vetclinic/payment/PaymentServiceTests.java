package com.vetclinic.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vetclinic.appointment.Actor;
import com.vetclinic.appointment.Appointment;
import com.vetclinic.appointment.AppointmentLifecycle;
import com.vetclinic.appointment.AppointmentQueryService;
import com.vetclinic.appointment.AppointmentRepository;
import com.vetclinic.appointment.AppointmentStatus;
import com.vetclinic.auth.CurrentUser;
import com.vetclinic.auth.Role;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ServiceCode;
import com.vetclinic.owner.Breed;
import com.vetclinic.owner.Owner;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.Species;
import com.vetclinic.payment.dto.PaymentRequest;
import com.vetclinic.payment.dto.PaymentResponse;
import com.vetclinic.shared.error.DomainException;
import com.vetclinic.veterinarian.ScheduleBlock;
import com.vetclinic.veterinarian.Veterinarian;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTests {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 2, 12, 0);
    static final CurrentUser RECEPTIONIST = new CurrentUser(3L, Role.RECEPTIONIST, null, null);
    static final CurrentUser VET = new CurrentUser(4L, Role.VETERINARIAN, null, 100L);

    @Mock AppointmentRepository appointments;
    @Mock PaymentRepository payments;

    PaymentService service;
    Appointment appointment; // total 40.00 + 20.00 = 60.00

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        AppointmentLifecycle lifecycle = new AppointmentLifecycle();
        AppointmentQueryService queries = new AppointmentQueryService(appointments, payments, null, lifecycle, clock);
        service = new PaymentService(payments, queries, lifecycle, clock);

        Owner owner = new Owner("Ann", "555-0001");
        ReflectionTestUtils.setField(owner, "id", 10L);
        Pet pet = new Pet("Rex", Species.DOG, Breed.BULLDOG, owner);
        ClinicService consultation = new ClinicService(ServiceCode.GENERAL_CONSULTATION, "General consultation", 30,
                new BigDecimal("40.00"));
        Veterinarian vet = new Veterinarian("Ana", "General", new BigDecimal("20.00"),
                List.of(new ScheduleBlock(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0))));
        ReflectionTestUtils.setField(vet, "id", 100L);
        appointment = new Appointment(pet, consultation, vet, NOW.minusHours(2), AppointmentStatus.FINISHED,
                Actor.RECEPTIONIST, null, null, NOW.minusDays(1));
        ReflectionTestUtils.setField(appointment, "id", 1L);
        when(appointments.findById(1L)).thenReturn(Optional.of(appointment));
    }

    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }

    private void stubSave() {
        when(payments.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void exactTotalOnFinishedAppointmentMarksItPaid() {
        stubSave();

        PaymentResponse response = service.pay(RECEPTIONIST, 1L, new PaymentRequest(new BigDecimal("60.00")));

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.PAID);
        assertThat(response.amount()).isEqualByComparingTo("60.00");
        assertThat(response.paidAt()).isEqualTo(NOW);
        assertThat(response.appointmentId()).isEqualTo(1L);
    }

    @Test
    void amountWithDifferentScaleButEqualValueIsAccepted() {
        stubSave();

        PaymentResponse response = service.pay(RECEPTIONIST, 1L, new PaymentRequest(new BigDecimal("60.0")));

        assertThat(response.amount()).isEqualByComparingTo("60.00");
        assertThat(response.amount().scale()).isEqualTo(2);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.PAID);
    }

    @Test
    void amountBelowTotalIsRejected() {
        assertCode(() -> service.pay(RECEPTIONIST, 1L, new PaymentRequest(new BigDecimal("59.99"))), "AMOUNT_MISMATCH");
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.FINISHED);
        verify(payments, never()).save(any());
    }

    @Test
    void amountAboveTotalIsRejected() {
        assertThatThrownBy(() -> service.pay(RECEPTIONIST, 1L, new PaymentRequest(new BigDecimal("60.01"))))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("AMOUNT_MISMATCH");
                    assertThat(e.getMessage()).isEqualTo("Payment must equal total 60.00");
                });
        verify(payments, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"SCHEDULED", "IN_PROGRESS"})
    void unfinishedAppointmentCannotBePaid(AppointmentStatus status) {
        ReflectionTestUtils.setField(appointment, "status", status);

        assertCode(() -> service.pay(RECEPTIONIST, 1L, new PaymentRequest(new BigDecimal("60.00"))),
                "INVALID_TRANSITION");
        verify(payments, never()).save(any());
    }

    @Test
    void secondPaymentIsRejected() {
        when(payments.existsByAppointmentId(1L)).thenReturn(true);

        assertCode(() -> service.pay(RECEPTIONIST, 1L, new PaymentRequest(new BigDecimal("60.00"))), "ALREADY_PAID");
        verify(payments, never()).save(any());
    }

    @Test
    void veterinarianCannotRecordPayments() {
        assertCode(() -> service.pay(VET, 1L, new PaymentRequest(new BigDecimal("60.00"))), "ACTION_NOT_ALLOWED");
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.FINISHED);
        verify(payments, never()).save(any());
    }
}
