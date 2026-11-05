package com.vetclinic.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private UserAccountRepository accounts;

	private String login(String username, String password) throws Exception {
		String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		JsonNode json = JsonMapper.builder().build().readTree(body);
		return json.get("token").asString();
	}

	@Test
	void receptionistLogsInAndReadsMe() throws Exception {
		String token = login("reception", "reception123");

		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("reception"))
			.andExpect(jsonPath("$.role").value("RECEPTIONIST"));
	}

	@Test
	void veterinarianLogsInAndReadsMe() throws Exception {
		String token = login("vet.ana", "vet123");

		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("VETERINARIAN"))
			.andExpect(jsonPath("$.veterinarianId").isNumber());
	}

	@Test
	void wrongPasswordReturns401() throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"reception\",\"password\":\"nope\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
	}

	@Test
	void expiredTokenReturns401() throws Exception {
		Clock past = Clock.fixed(Instant.now().minus(Duration.ofDays(1)), ZoneOffset.UTC);
		String token = new TokenService(jwtEncoder, past, Duration.ofHours(8))
			.issue(accounts.findByUsername("reception").orElseThrow());

		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void healthIsPublic() throws Exception {
		mockMvc.perform(get("/api/health")).andExpect(status().isOk());
	}
}
