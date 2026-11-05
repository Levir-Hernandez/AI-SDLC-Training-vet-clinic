package com.vetclinic.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.vetclinic.auth.dto.CurrentUserResponse;
import com.vetclinic.auth.dto.LoginResponse;
import com.vetclinic.config.SecurityConfig;
import com.vetclinic.shared.error.DomainException;
import com.vetclinic.shared.error.GlobalExceptionHandler;
import com.vetclinic.support.TestJwt;

@WebMvcTest(AuthController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class })
class AuthControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

	@Test
	void validLoginReturnsTokenPayload() throws Exception {
		when(authService.login(any()))
			.thenReturn(new LoginResponse("tok", Instant.parse("2030-01-01T00:00:00Z"), Role.RECEPTIONIST, "Reception"));

		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"reception\",\"password\":\"reception123\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.token").value("tok"))
			.andExpect(jsonPath("$.role").value("RECEPTIONIST"))
			.andExpect(jsonPath("$.displayName").value("Reception"));
	}

	@Test
	void blankUsernameIsRejectedWithFieldError() throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"\",\"password\":\"x\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("username"));
	}

	@Test
	void badCredentialsReturn401() throws Exception {
		when(authService.login(any()))
			.thenThrow(new DomainException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Invalid username or password"));

		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"a\",\"password\":\"b\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
	}

	@Test
	void meWithValidTokenReturnsUser() throws Exception {
		when(authService.me(any())).thenReturn(new CurrentUserResponse(1L, "reception", Role.RECEPTIONIST, "Reception", null, null));

		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + TestJwt.receptionist()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("reception"))
			.andExpect(jsonPath("$.role").value("RECEPTIONIST"));
	}

	@Test
	void meWithoutTokenReturns401() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void meWithTokenSignedByAnotherKeyReturns401() throws Exception {
		String forged = TestJwt.token("another-secret-another-secret-another-secret", Role.RECEPTIONIST, 1L, null, null);

		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + forged))
			.andExpect(status().isUnauthorized());
	}
}
