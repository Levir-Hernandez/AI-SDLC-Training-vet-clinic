package com.vetclinic.appointment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.vetclinic.appointment.dto.AppointmentResponse;
import com.vetclinic.config.SecurityConfig;
import com.vetclinic.owner.Breed;
import com.vetclinic.owner.Species;
import com.vetclinic.shared.PageResponse;
import com.vetclinic.shared.error.ConflictException;
import com.vetclinic.shared.error.GlobalExceptionHandler;
import com.vetclinic.support.TestJwt;

@WebMvcTest(AppointmentController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class })
class AppointmentControllerTests {

	private static final String BODY = "{\"petId\":1,\"serviceId\":1,\"veterinarianId\":1,\"startAt\":\"2030-01-07T09:00:00\"}";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BookingService booking;

	@MockitoBean
	private AppointmentQueryService queries;

	@MockitoBean
	private AppointmentActionService actions;

	private AppointmentResponse response() {
		LocalDateTime start = LocalDateTime.of(2030, 1, 7, 9, 0);
		return new AppointmentResponse(5L, new AppointmentResponse.PetRef(1L, "Rex", Species.DOG, Breed.BULLDOG), "Ann",
				new AppointmentResponse.ServiceRef(1L, "Checkup", 30), new AppointmentResponse.VetRef(1L, "Dr. Ana"),
				start, start.plusMinutes(30), AppointmentStatus.SCHEDULED, Actor.RECEPTIONIST, new BigDecimal("60.00"),
				null, null, null, null, null, false, true, Set.of(AppointmentAction.START));
	}

	@Test
	void receptionistCreatesAppointment() throws Exception {
		when(booking.book(any(), any())).thenReturn(response());

		mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(BODY)
			.header("Authorization", "Bearer " + TestJwt.receptionist()))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "/api/appointments/5"))
			.andExpect(jsonPath("$.status").value("SCHEDULED"));
	}

	@Test
	void missingPetIdIsBadRequest() throws Exception {
		mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"serviceId\":1,\"veterinarianId\":1,\"startAt\":\"2030-01-07T09:00:00\"}")
			.header("Authorization", "Bearer " + TestJwt.receptionist()))
			.andExpect(status().isBadRequest());
	}

	@Test
	void slotTakenIsConflict() throws Exception {
		when(booking.book(any(), any())).thenThrow(new ConflictException("SLOT_TAKEN", "taken"));

		mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(BODY)
			.header("Authorization", "Bearer " + TestJwt.receptionist()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SLOT_TAKEN"));
	}

	@Test
	void veterinarianCannotCreate() throws Exception {
		mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(BODY)
			.header("Authorization", "Bearer " + TestJwt.veterinarian(1L)))
			.andExpect(status().isForbidden());
	}

	@Test
	void missingTokenIsUnauthorized() throws Exception {
		mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(BODY))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void ownerConfirmsReservation() throws Exception {
		when(actions.confirm(any(), any())).thenReturn(response());

		mockMvc.perform(post("/api/appointments/5/confirm").header("Authorization", "Bearer " + TestJwt.owner(1L)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SCHEDULED"));
	}

	@Test
	void veterinarianCannotConfirm() throws Exception {
		mockMvc.perform(post("/api/appointments/5/confirm").header("Authorization", "Bearer " + TestJwt.veterinarian(1L)))
			.andExpect(status().isForbidden());
	}

	@Test
	void expiredReservationConfirmIsConflict() throws Exception {
		when(actions.confirm(any(), any())).thenThrow(new ConflictException("RESERVATION_EXPIRED", "expired"));

		mockMvc.perform(post("/api/appointments/5/confirm").header("Authorization", "Bearer " + TestJwt.owner(1L)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_EXPIRED"));
	}

	@Test
	void listIsPaginated() throws Exception {
		when(queries.list(any(), any(), any())).thenReturn(new PageResponse<>(List.of(response()), 0, 20, 1));

		mockMvc.perform(get("/api/appointments").header("Authorization", "Bearer " + TestJwt.receptionist()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].id").value(5));
	}

	@Test
	void detailReturnsAllowedActions() throws Exception {
		when(queries.get(any(), any())).thenReturn(response());

		mockMvc.perform(get("/api/appointments/5").header("Authorization", "Bearer " + TestJwt.receptionist()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.allowedActions[0]").value("START"));
	}
}
