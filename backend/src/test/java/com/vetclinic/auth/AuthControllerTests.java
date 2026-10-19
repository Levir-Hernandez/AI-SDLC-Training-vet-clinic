package com.vetclinic.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vetclinic.auth.dto.CurrentUserResponse;
import com.vetclinic.auth.dto.LoginResponse;
import com.vetclinic.config.ClockConfig;
import com.vetclinic.config.SecurityConfig;
import com.vetclinic.shared.error.DomainException;
import com.vetclinic.shared.error.GlobalExceptionHandler;
import com.vetclinic.support.TestJwt;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, ClockConfig.class, GlobalExceptionHandler.class})
class AuthControllerTests {

    @Autowired MockMvc mockMvc;
    @MockitoBean AuthService authService;

    @Test
    void validLoginReturnsTokenPayload() throws Exception {
        when(authService.login(any())).thenReturn(
                new LoginResponse("jwt-token", Instant.parse("2030-01-01T00:00:00Z"), Role.RECEPTIONIST, "Reception"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"reception\",\"password\":\"reception123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.role").value("RECEPTIONIST"))
                .andExpect(jsonPath("$.displayName").value("Reception"));
    }

    @Test
    void meReturnsTheCurrentUser() throws Exception {
        when(authService.me(any())).thenReturn(new CurrentUserResponse(1L, "reception", Role.RECEPTIONIST, "Reception"));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + TestJwt.receptionist()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.username").value("reception"))
                .andExpect(jsonPath("$.role").value("RECEPTIONIST"));
    }

    @Test
    void blankFieldsAreRejectedWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'username')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists());
    }

    @Test
    void badCredentialsReturn401WithCode() throws Exception {
        when(authService.login(any())).thenThrow(
                new DomainException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Invalid username or password"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"reception\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void meWithoutTokenReturns401ProblemJson() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void meWithTokenSignedByAnotherKeyReturns401() throws Exception {
        String forged = TestJwt.signedWith("another-secret-another-secret-another-secret");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
