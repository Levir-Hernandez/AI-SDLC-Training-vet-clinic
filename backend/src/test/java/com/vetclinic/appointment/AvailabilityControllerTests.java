package com.vetclinic.appointment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.vetclinic.appointment.dto.SlotResponse;
import com.vetclinic.config.ClockConfig;
import com.vetclinic.config.SecurityConfig;
import com.vetclinic.shared.error.GlobalExceptionHandler;
import com.vetclinic.support.TestJwt;

@WebMvcTest(AvailabilityController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class, ClockConfig.class })
class AvailabilityControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AvailabilityService availability;

	private void stub() {
		LocalDateTime start = LocalDateTime.of(2030, 1, 7, 9, 0);
		when(availability.slots(any(), any(), any()))
			.thenReturn(List.of(new SlotResponse(1L, "Dr. Ana", start, start.plusMinutes(30))));
	}

	@Test
	void receptionistListsSlots() throws Exception {
		stub();
		mockMvc.perform(get("/api/availability").param("serviceId", "1").header("Authorization", "Bearer " + TestJwt.receptionist()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].veterinarianName").value("Dr. Ana"));
	}

	@Test
	void ownerListsSlots() throws Exception {
		stub();
		mockMvc.perform(get("/api/availability").param("serviceId", "1").param("date", "2030-01-07")
			.header("Authorization", "Bearer " + TestJwt.owner(1L)))
			.andExpect(status().isOk());
	}

	@Test
	void veterinarianIsForbidden() throws Exception {
		mockMvc.perform(get("/api/availability").param("serviceId", "1").header("Authorization", "Bearer " + TestJwt.veterinarian(1L)))
			.andExpect(status().isForbidden());
	}

	@Test
	void missingTokenIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/availability").param("serviceId", "1")).andExpect(status().isUnauthorized());
	}

	@Test
	void missingServiceIdIsBadRequest() throws Exception {
		mockMvc.perform(get("/api/availability").header("Authorization", "Bearer " + TestJwt.receptionist()))
			.andExpect(status().isBadRequest());
	}
}
