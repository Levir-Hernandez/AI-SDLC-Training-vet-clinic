package com.vetclinic.veterinarian;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vetclinic.auth.Role;
import com.vetclinic.config.ClockConfig;
import com.vetclinic.config.SecurityConfig;
import com.vetclinic.shared.PageResponse;
import com.vetclinic.shared.error.BadRequestException;
import com.vetclinic.shared.error.GlobalExceptionHandler;
import com.vetclinic.support.TestJwt;
import com.vetclinic.veterinarian.dto.ScheduleBlockDto;
import com.vetclinic.veterinarian.dto.VeterinarianResponse;
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

@WebMvcTest(VeterinarianController.class)
@Import({SecurityConfig.class, ClockConfig.class, GlobalExceptionHandler.class})
class VeterinarianControllerTests {

    private static final String VALID = """
            {"name":"Dr. Eva","specialization":"Dermatology","costPerService":15.00,
             "username":"vet.eva","password":"secret1",
             "schedule":[{"day":"MONDAY","start":"09:00","end":"13:00"}]}""";

    @Autowired MockMvc mockMvc;
    @MockitoBean VeterinarianService veterinarianService;

    private static VeterinarianResponse sample() {
        return new VeterinarianResponse(5L, "Dr. Eva", "Dermatology", new BigDecimal("15.00"),
                List.of(new ScheduleBlockDto(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0))));
    }

    private String receptionist() {
        return "Bearer " + TestJwt.receptionist();
    }

    @Test
    void receptionistCreatesVeterinarianWith201AndLocation() throws Exception {
        when(veterinarianService.create(any())).thenReturn(sample());

        mockMvc.perform(post("/api/veterinarians").header("Authorization", receptionist())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/veterinarians/5"))
                .andExpect(jsonPath("$.name").value("Dr. Eva"));
    }

    @Test
    void anyRoleCanListVeterinarians() throws Exception {
        when(veterinarianService.list(any())).thenReturn(new PageResponse<>(List.of(sample()), 0, 20, 1));

        mockMvc.perform(get("/api/veterinarians").header("Authorization", "Bearer " + TestJwt.forRole(Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].specialization").value("Dermatology"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void missingNameIsRejectedWithFieldError() throws Exception {
        mockMvc.perform(post("/api/veterinarians").header("Authorization", receptionist())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID.replace("\"Dr. Eva\"", "\"\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists());
        verifyNoInteractions(veterinarianService);
    }

    @Test
    void negativeCostIsRejectedWithFieldError() throws Exception {
        mockMvc.perform(post("/api/veterinarians").header("Authorization", receptionist())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID.replace("15.00", "-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'costPerService')]").exists());
    }

    @Test
    void invalidScheduleFromServiceReturns400WithCode() throws Exception {
        when(veterinarianService.create(any())).thenThrow(
                new BadRequestException("INVALID_SCHEDULE", "Schedule blocks overlap on MONDAY"));

        mockMvc.perform(post("/api/veterinarians").header("Authorization", receptionist())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SCHEDULE"));
    }

    @Test
    void ownerCannotRegisterVeterinarian() throws Exception {
        mockMvc.perform(post("/api/veterinarians")
                        .header("Authorization", "Bearer " + TestJwt.forRole(Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());
    }

    @Test
    void veterinarianCannotRegisterVeterinarian() throws Exception {
        mockMvc.perform(post("/api/veterinarians")
                        .header("Authorization", "Bearer " + TestJwt.forRole(Role.VETERINARIAN))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());
    }

    @Test
    void listWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/veterinarians")).andExpect(status().isUnauthorized());
    }
}
