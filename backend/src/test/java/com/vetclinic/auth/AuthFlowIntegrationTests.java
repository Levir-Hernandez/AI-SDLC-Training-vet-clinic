package com.vetclinic.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vetclinic.seed.DemoDataSeeder;
import com.vetclinic.support.TestJwt;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTests {

    @Autowired MockMvc mockMvc;
    @Autowired UserAccountRepository accounts;
    @Autowired AccountService accountService;
    @Autowired com.vetclinic.catalog.ClinicServiceRepository services;
    @Autowired com.vetclinic.veterinarian.VeterinarianService veterinarians;
    @Autowired JsonMapper mapper;

    @BeforeEach
    void seed() {
        // Seeding is disabled in tests (app.seed.enabled=false); run it explicitly. It is idempotent.
        new DemoDataSeeder(accounts, accountService, services, veterinarians).run();
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("token").asString();
    }

    @Test
    void receptionistLogsInAndReadsTheirIdentity() throws Exception {
        String token = login("reception", "reception123");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("reception"))
                .andExpect(jsonPath("$.role").value("RECEPTIONIST"));
    }

    @Test
    void veterinarianLogsInAndReadsTheirIdentity() throws Exception {
        String token = login("vet.ana", "vet123");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("vet.ana"))
                .andExpect(jsonPath("$.role").value("VETERINARIAN"));
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameError() throws Exception {
        for (String body : new String[] {
                "{\"username\":\"reception\",\"password\":\"nope\"}",
                "{\"username\":\"ghost\",\"password\":\"nope\"}"}) {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"))
                    .andExpect(jsonPath("$.detail").value("Invalid username or password"));
        }
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String expired = TestJwt.token(TestJwt.TEST_SECRET, 1L, Role.RECEPTIONIST, null, null,
                Instant.now().minusSeconds(120));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }
}
