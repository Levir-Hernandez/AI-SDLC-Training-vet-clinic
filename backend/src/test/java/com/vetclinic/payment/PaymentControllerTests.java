package com.vetclinic.payment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vetclinic.auth.Role;
import com.vetclinic.config.ClockConfig;
import com.vetclinic.config.SecurityConfig;
import com.vetclinic.payment.dto.PaymentResponse;
import com.vetclinic.shared.error.ConflictException;
import com.vetclinic.shared.error.GlobalExceptionHandler;
import com.vetclinic.support.TestJwt;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, ClockConfig.class, GlobalExceptionHandler.class})
class PaymentControllerTests {

    static final String URL = "/api/appointments/5/payments";

    @Autowired MockMvc mockMvc;
    @MockitoBean PaymentService payments;

    private org.springframework.test.web.servlet.ResultActions pay(String token, String body) throws Exception {
        return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("Authorization", "Bearer " + token));
    }

    @Test
    void receptionistRecordsPayment() throws Exception {
        when(payments.pay(any(), any(), any())).thenReturn(
                new PaymentResponse(1L, 5L, new BigDecimal("60.00"), LocalDateTime.of(2030, 1, 7, 11, 0)));

        pay(TestJwt.receptionist(), "{\"amount\":\"60.00\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentId").value(5))
                .andExpect(jsonPath("$.amount").value(60.00));
    }

    @Test
    void missingAmountReturns400() throws Exception {
        pay(TestJwt.receptionist(), "{}").andExpect(status().isBadRequest());
    }

    @Test
    void negativeAmountReturns400() throws Exception {
        pay(TestJwt.receptionist(), "{\"amount\":\"-1.00\"}").andExpect(status().isBadRequest());
    }

    @Test
    void amountMismatchReturns409() throws Exception {
        when(payments.pay(any(), any(), any())).thenThrow(new ConflictException("AMOUNT_MISMATCH", "no"));

        pay(TestJwt.receptionist(), "{\"amount\":\"59.99\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AMOUNT_MISMATCH"));
    }

    @Test
    void ownerCannotPay() throws Exception {
        pay(TestJwt.forRole(Role.OWNER), "{\"amount\":\"60.00\"}").andExpect(status().isForbidden());
    }

    @Test
    void veterinarianCannotPay() throws Exception {
        pay(TestJwt.forRole(Role.VETERINARIAN), "{\"amount\":\"60.00\"}").andExpect(status().isForbidden());
    }
}
