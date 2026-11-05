package com.vetclinic.veterinarian;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.vetclinic.config.SecurityConfig;
import com.vetclinic.shared.PageResponse;
import com.vetclinic.shared.error.BadRequestException;
import com.vetclinic.shared.error.GlobalExceptionHandler;
import com.vetclinic.support.TestJwt;
import com.vetclinic.veterinarian.dto.ScheduleBlockDto;
import com.vetclinic.veterinarian.dto.VeterinarianResponse;

@WebMvcTest(VeterinarianController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class })
class VeterinarianControllerTests {

	private static final String VALID = """
			{"name":"Dr. New","specialization":"Surgery","costPerService":50.00,"username":"vet.new",
			"password":"secret123","schedule":[{"day":"MONDAY","start":"09:00","end":"13:00"}]}""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private VeterinarianService veterinarianService;

	private VeterinarianResponse response() {
		return new VeterinarianResponse(7L, "Dr. New", "Surgery", new BigDecimal("50.00"),
				List.of(new ScheduleBlockDto(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0))));
	}

	private MockHttpServletRequestBuilder postAs(String token, String body) {
		return post("/api/veterinarians").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body);
	}

	@Test
	void receptionistCreatesVeterinarianWithLocation() throws Exception {
		when(veterinarianService.create(any())).thenReturn(response());

		mockMvc.perform(postAs(TestJwt.receptionist(), VALID))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "/api/veterinarians/7"))
			.andExpect(jsonPath("$.id").value(7));
	}

	@Test
	void anyRoleCanListVeterinarians() throws Exception {
		when(veterinarianService.list(any())).thenReturn(new PageResponse<>(List.of(response()), 0, 20, 1));

		mockMvc.perform(get("/api/veterinarians").header("Authorization", "Bearer " + TestJwt.owner(1L)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].name").value("Dr. New"))
			.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void missingNameIsRejectedWithFieldError() throws Exception {
		String body = VALID.replace("\"name\":\"Dr. New\",", "");

		mockMvc.perform(postAs(TestJwt.receptionist(), body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("name"));
	}

	@Test
	void negativeCostIsRejectedWithFieldError() throws Exception {
		String body = VALID.replace("50.00", "-1");

		mockMvc.perform(postAs(TestJwt.receptionist(), body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("costPerService"));
	}

	@Test
	void invalidScheduleFromServiceReturns400WithCode() throws Exception {
		when(veterinarianService.create(any()))
			.thenThrow(new BadRequestException("INVALID_SCHEDULE", "Overlapping blocks on MONDAY"));

		mockMvc.perform(postAs(TestJwt.receptionist(), VALID))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_SCHEDULE"));
	}

	@Test
	void ownerCannotCreateVeterinarian() throws Exception {
		mockMvc.perform(postAs(TestJwt.owner(1L), VALID)).andExpect(status().isForbidden());
	}

	@Test
	void createWithoutTokenReturns401() throws Exception {
		mockMvc.perform(post("/api/veterinarians").contentType(MediaType.APPLICATION_JSON).content(VALID))
			.andExpect(status().isUnauthorized());
	}
}
