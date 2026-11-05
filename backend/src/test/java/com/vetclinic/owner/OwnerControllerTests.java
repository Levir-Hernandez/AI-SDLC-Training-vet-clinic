package com.vetclinic.owner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.vetclinic.owner.dto.OwnerResponse;
import com.vetclinic.shared.error.ConflictException;
import com.vetclinic.shared.error.GlobalExceptionHandler;
import com.vetclinic.support.TestJwt;

@WebMvcTest(OwnerController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class })
class OwnerControllerTests {

	private static final String VALID = """
			{"name":"Ann","phone":"+1 555 0100","password":"secret123","pets":[]}""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OwnerService ownerService;

	@MockitoBean
	private PetService petService;

	private MockHttpServletRequestBuilder postAs(String token, String body) {
		return post("/api/owners").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body);
	}

	@Test
	void receptionistCreatesOwnerWithLocation() throws Exception {
		when(ownerService.create(any())).thenReturn(new OwnerResponse(5L, "Ann", "+1 555 0100", List.of()));

		mockMvc.perform(postAs(TestJwt.receptionist(), VALID))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "/api/owners/5"))
			.andExpect(jsonPath("$.id").value(5));
	}

	@Test
	void missingNameIsRejectedWithFieldError() throws Exception {
		mockMvc.perform(postAs(TestJwt.receptionist(), VALID.replace("\"name\":\"Ann\",", "")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("name"));
	}

	@Test
	void missingPhoneIsRejectedWithFieldError() throws Exception {
		mockMvc.perform(postAs(TestJwt.receptionist(), VALID.replace("\"phone\":\"+1 555 0100\",", "")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("phone"));
	}

	@Test
	void duplicatePhoneReturns409() throws Exception {
		when(ownerService.create(any())).thenThrow(new ConflictException("PHONE_TAKEN", "taken"));

		mockMvc.perform(postAs(TestJwt.receptionist(), VALID))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PHONE_TAKEN"));
	}

	@Test
	void ownerCannotCreateOwner() throws Exception {
		mockMvc.perform(postAs(TestJwt.owner(1L), VALID)).andExpect(status().isForbidden());
	}

	@Test
	void createWithoutTokenReturns401() throws Exception {
		mockMvc.perform(post("/api/owners").contentType(MediaType.APPLICATION_JSON).content(VALID))
			.andExpect(status().isUnauthorized());
	}
}
