package com.vetclinic.payment;

import static com.vetclinic.appointment.Fixtures.NOW;
import static com.vetclinic.appointment.Fixtures.START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vetclinic.appointment.Appointment;
import com.vetclinic.appointment.AppointmentLifecycle;
import com.vetclinic.appointment.AppointmentQueryService;
import com.vetclinic.appointment.AppointmentRepository;
import com.vetclinic.appointment.AppointmentStatus;
import com.vetclinic.appointment.Fixtures;
import com.vetclinic.appointment.Fixtures.MutableClock;
import com.vetclinic.auth.CurrentUser;
import com.vetclinic.owner.PetService;
import com.vetclinic.payment.dto.PaymentRequest;
import com.vetclinic.payment.dto.PaymentResponse;
import com.vetclinic.shared.error.DomainException;

class PaymentServiceTests {

	AppointmentRepository appointments = mock(AppointmentRepository.class);
	PaymentRepository payments = mock(PaymentRepository.class);
	MutableClock clock = new MutableClock(NOW);
	AppointmentLifecycle lifecycle = new AppointmentLifecycle();
	AppointmentQueryService queries = new AppointmentQueryService(appointments, payments, mock(PetService.class),
			lifecycle, clock);
	PaymentService service = new PaymentService(queries, payments, lifecycle, clock);
	CurrentUser reception = Fixtures.receptionist();

	@BeforeEach
	void setUp() {
		when(payments.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
	}

	private Appointment stored(AppointmentStatus status) {
		Appointment a = Fixtures.appointment(1L, Fixtures.pet(7L, Fixtures.owner(1L)), Fixtures.service(2L),
				Fixtures.vet(5L), START, status);
		when(appointments.findById(1L)).thenReturn(Optional.of(a));
		return a;
	}

	private void code(Runnable r, String code) {
		assertThatThrownBy(r::run).isInstanceOf(DomainException.class)
			.extracting(e -> ((DomainException) e).getCode())
			.isEqualTo(code);
	}

	private PaymentRequest amount(String value) {
		return new PaymentRequest(new BigDecimal(value));
	}

	@Test
	void exactTotalMovesFinishedToPaid() {
		Appointment a = stored(AppointmentStatus.FINISHED);

		PaymentResponse res = service.pay(reception, 1L, amount("60.00"));

		assertThat(a.getStatus()).isEqualTo(AppointmentStatus.PAID);
		assertThat(res.amount()).isEqualByComparingTo("60.00");
		assertThat(res.paidAt()).isEqualTo(NOW);
		assertThat(res.appointmentStatus()).isEqualTo(AppointmentStatus.PAID);
	}

	@Test
	void sixtyPointZeroEqualsTotal() {
		stored(AppointmentStatus.FINISHED);

		PaymentResponse res = service.pay(reception, 1L, amount("60.0"));

		assertThat(res.amount().scale()).isEqualTo(2);
	}

	@Test
	void belowTotalIsRejected() {
		stored(AppointmentStatus.FINISHED);

		code(() -> service.pay(reception, 1L, amount("59.99")), "AMOUNT_MISMATCH");
		verify(payments, never()).save(any());
	}

	@Test
	void aboveTotalIsRejected() {
		stored(AppointmentStatus.FINISHED);

		code(() -> service.pay(reception, 1L, amount("60.01")), "AMOUNT_MISMATCH");
	}

	@Test
	void scheduledAndInProgressCannotBePaid() {
		stored(AppointmentStatus.SCHEDULED);
		code(() -> service.pay(reception, 1L, amount("60.00")), "INVALID_TRANSITION");
		stored(AppointmentStatus.IN_PROGRESS);
		code(() -> service.pay(reception, 1L, amount("60.00")), "INVALID_TRANSITION");
	}

	@Test
	void secondPaymentIsRejected() {
		stored(AppointmentStatus.PAID);
		when(payments.existsByAppointmentId(1L)).thenReturn(true);

		code(() -> service.pay(reception, 1L, amount("60.00")), "ALREADY_PAID");
	}

	@Test
	void veterinarianCannotPay() {
		stored(AppointmentStatus.FINISHED);

		code(() -> service.pay(Fixtures.vetUser(5L), 1L, amount("60.00")), "ACTION_NOT_ALLOWED");
	}
}
