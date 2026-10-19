package com.vetclinic.payment;

import com.vetclinic.appointment.Appointment;
import com.vetclinic.appointment.AppointmentAction;
import com.vetclinic.appointment.AppointmentLifecycle;
import com.vetclinic.appointment.AppointmentQueryService;
import com.vetclinic.appointment.TransitionContext;
import com.vetclinic.auth.CurrentUser;
import com.vetclinic.payment.dto.PaymentRequest;
import com.vetclinic.payment.dto.PaymentResponse;
import com.vetclinic.shared.error.ConflictException;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final AppointmentQueryService queries;
    private final AppointmentLifecycle lifecycle;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, AppointmentQueryService queries,
                          AppointmentLifecycle lifecycle, Clock clock) {
        this.payments = payments;
        this.queries = queries;
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    /** TASK.md: one payment per finished appointment, for exactly the total. */
    @Transactional
    public PaymentResponse pay(CurrentUser user, Long appointmentId, PaymentRequest request) {
        Appointment a = queries.requireScoped(user, appointmentId);
        if (payments.existsByAppointmentId(a.getId())) {
            throw new ConflictException("ALREADY_PAID", "The appointment already has a payment");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        TransitionContext ctx = new TransitionContext(null, now);
        lifecycle.validate(a, AppointmentAction.PAY, user.actor(), ctx);
        if (request.amount().setScale(2, RoundingMode.HALF_UP).compareTo(a.getTotal()) != 0) {
            throw new ConflictException("AMOUNT_MISMATCH", "Payment must equal total " + a.getTotal());
        }
        Payment payment = payments.save(new Payment(a, request.amount(), now));
        lifecycle.apply(a, AppointmentAction.PAY, user.actor(), ctx);
        return PaymentResponse.from(payment);
    }
}
