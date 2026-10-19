package com.vetclinic.appointment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vetclinic.appointment.dto.SlotResponse;
import com.vetclinic.auth.Role;
import com.vetclinic.config.ClockConfig;
import com.vetclinic.config.SecurityConfig;
import com.vetclinic.shared.error.GlobalExceptionHandler;
import com.vetclinic.support.TestJwt;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AvailabilityController.class)
@Import({SecurityConfig.class, ClockConfig.class, GlobalExceptionHandler.class})
class AvailabilityControllerTests {

    @Autowired MockMvc mockMvc;
    @MockitoBean AvailabilityService availability;

    private void stubSlots() {
        when(availability.slots(any(), any(), any())).thenReturn(List.of(new SlotResponse(1L, "Ana",
                LocalDateTime.of(2030, 1, 7, 10, 0), LocalDateTime.of(2030, 1, 7, 10, 30))));
    }

    @Test
    void ownerAndReceptionistListSlots() throws Exception {
        stubSlots();
        for (Role role : new Role[] {Role.OWNER, Role.RECEPTIONIST}) {
            mockMvc.perform(get("/api/availability").param("serviceId", "1").param("date", "2030-01-07")
                            .header("Authorization", "Bearer " + TestJwt.forRole(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].veterinarianName").value("Ana"));
        }
    }

    @Test
    void veterinarianIsForbidden() throws Exception {
        mockMvc.perform(get("/api/availability").param("serviceId", "1")
                        .header("Authorization", "Bearer " + TestJwt.forRole(Role.VETERINARIAN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void noTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/availability").param("serviceId", "1")).andExpect(status().isUnauthorized());
    }

    @Test
    void missingServiceIdReturns400() throws Exception {
        mockMvc.perform(get("/api/availability").header("Authorization", "Bearer " + TestJwt.receptionist()))
                .andExpect(status().isBadRequest());
    }
}
