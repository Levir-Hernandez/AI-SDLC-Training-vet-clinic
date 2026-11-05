package com.vetclinic.payment;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vetclinic.appointment.Appointment;
import com.vetclinic.appointment.AppointmentAction;
import com.vetclinic.appointment.AppointmentLifecycle;
import com.vetclinic.appointment.AppointmentQueryService;
import com.vetclinic.appointment.TransitionContext;
import com.vetclinic.auth.CurrentUser;
import com.vetclinic.payment.dto.PaymentRequest;
import com.vetclinic.payment.dto.PaymentResponse;
import com.vetclinic.shared.error.ConflictException;

@Service
public class PaymentService {

	private final AppointmentQueryService queries;
	private final PaymentRepository payments;
	private final AppointmentLifecycle lifecycle;
	private final Clock clock;

	public PaymentService(AppointmentQueryService queries, PaymentRepository payments, AppointmentLifecycle lifecycle,
			Clock clock) {
		this.queries = queries;
		this.payments = payments;
		this.lifecycle = lifecycle;
		this.clock = clock;
	}

	/** One payment per finished appointment, exactly equal to the total (TASK.md payment rules). */
	@Transactional
	public PaymentResponse pay(CurrentUser user, Long appointmentId, PaymentRequest req) {
		Appointment a = queries.requireScoped(user, appointmentId);
		if (payments.existsByAppointmentId(a.getId())) {
			throw new ConflictException("ALREADY_PAID", "The appointment already has a payment");
		}
		LocalDateTime now = LocalDateTime.now(clock);
		lifecycle.apply(a, AppointmentAction.PAY, user.actor(), new TransitionContext(null, now));
		if (req.amount().compareTo(a.getTotal()) != 0) {
			throw new ConflictException("AMOUNT_MISMATCH", "Payment must equal total " + a.getTotal().toPlainString());
		}
		Payment saved = payments.save(new Payment(a, req.amount(), now));
		return new PaymentResponse(saved.getId(), a.getId(), saved.getAmount(), saved.getPaidAt(), a.getStatus());
	}
}
