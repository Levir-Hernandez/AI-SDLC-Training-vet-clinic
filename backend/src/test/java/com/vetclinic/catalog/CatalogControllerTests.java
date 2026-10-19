package com.vetclinic.catalog;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vetclinic.auth.Role;
import com.vetclinic.catalog.dto.ServiceResponse;
import com.vetclinic.config.ClockConfig;
import com.vetclinic.config.SecurityConfig;
import com.vetclinic.shared.error.GlobalExceptionHandler;
import com.vetclinic.support.TestJwt;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CatalogController.class)
@Import({SecurityConfig.class, ClockConfig.class, GlobalExceptionHandler.class})
class CatalogControllerTests {

    @Autowired MockMvc mockMvc;
    @MockitoBean CatalogService catalogService;

    @Test
    void listReturnsServicesForAnyAuthenticatedRole() throws Exception {
        when(catalogService.list()).thenReturn(List.of(
                new ServiceResponse(1L, ServiceCode.values()[0], "General consultation", 30, new BigDecimal("40.00"))));

        mockMvc.perform(get("/api/services").header("Authorization", "Bearer " + TestJwt.forRole(Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("General consultation"))
                .andExpect(jsonPath("$[0].durationMinutes").value(30))
                .andExpect(jsonPath("$[0].basePrice").value(40.00));
    }

    @Test
    void listWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/services"))
                .andExpect(status().isUnauthorized());
    }
}
