package com.vetclinic.payment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.vetclinic.appointment.AppointmentStatus;
import com.vetclinic.config.SecurityConfig;
import com.vetclinic.payment.dto.PaymentResponse;
import com.vetclinic.shared.error.ConflictException;
import com.vetclinic.shared.error.GlobalExceptionHandler;
import com.vetclinic.support.TestJwt;

@WebMvcTest(PaymentController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class })
class PaymentControllerTests {

	private static final String URL = "/api/appointments/5/payments";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PaymentService payments;

	@Test
	void receptionistPaysAndGetsCreated() throws Exception {
		when(payments.pay(any(), any(), any())).thenReturn(new PaymentResponse(1L, 5L, new BigDecimal("60.00"),
				LocalDateTime.of(2030, 1, 7, 10, 0), AppointmentStatus.PAID));

		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"60.00\"}")
			.header("Authorization", "Bearer " + TestJwt.receptionist()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.appointmentStatus").value("PAID"));
	}

	@Test
	void missingAmountIsBadRequest() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}")
			.header("Authorization", "Bearer " + TestJwt.receptionist()))
			.andExpect(status().isBadRequest());
	}

	@Test
	void negativeAmountIsBadRequest() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"-1.00\"}")
			.header("Authorization", "Bearer " + TestJwt.receptionist()))
			.andExpect(status().isBadRequest());
	}

	@Test
	void mismatchIsConflict() throws Exception {
		when(payments.pay(any(), any(), any())).thenThrow(new ConflictException("AMOUNT_MISMATCH", "no"));

		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"59.99\"}")
			.header("Authorization", "Bearer " + TestJwt.receptionist()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("AMOUNT_MISMATCH"));
	}

	@Test
	void ownerCannotPay() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"60.00\"}")
			.header("Authorization", "Bearer " + TestJwt.owner(1L)))
			.andExpect(status().isForbidden());
	}

	@Test
	void veterinarianCannotPay() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"60.00\"}")
			.header("Authorization", "Bearer " + TestJwt.veterinarian(1L)))
			.andExpect(status().isForbidden());
	}
}
