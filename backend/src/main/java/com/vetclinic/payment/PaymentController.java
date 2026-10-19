package com.vetclinic.payment;

import com.vetclinic.auth.CurrentUser;
import com.vetclinic.payment.dto.PaymentRequest;
import com.vetclinic.payment.dto.PaymentResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments/{id}/payments")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    /** Reception records the single, exact payment of a finished appointment. */
    @PostMapping
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<PaymentResponse> pay(Authentication authentication, @PathVariable Long id,
                                               @Valid @RequestBody PaymentRequest request) {
        PaymentResponse created = payments.pay(CurrentUser.from(authentication), id, request);
        return ResponseEntity.created(URI.create("/api/appointments/" + id + "/payments/" + created.id()))
                .body(created);
    }
}
