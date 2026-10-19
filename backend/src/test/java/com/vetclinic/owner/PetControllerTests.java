package com.vetclinic.owner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vetclinic.auth.Role;
import com.vetclinic.config.ClockConfig;
import com.vetclinic.config.SecurityConfig;
import com.vetclinic.owner.dto.PetResponse;
import com.vetclinic.shared.PageResponse;
import com.vetclinic.shared.error.GlobalExceptionHandler;
import com.vetclinic.support.TestJwt;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PetController.class)
@Import({SecurityConfig.class, ClockConfig.class, GlobalExceptionHandler.class})
class PetControllerTests {

    @Autowired MockMvc mockMvc;
    @MockitoBean PetService petService;

    @Test
    void ownerListsPets() throws Exception {
        when(petService.listFor(any(), any(), any())).thenReturn(new PageResponse<>(
                List.of(new PetResponse(1L, "Rex", Species.DOG, Breed.BULLDOG, 7L)), 0, 50, 1));

        mockMvc.perform(get("/api/pets").header("Authorization", "Bearer " + TestJwt.forRole(Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Rex"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void veterinarianIsForbidden() throws Exception {
        mockMvc.perform(get("/api/pets").header("Authorization", "Bearer " + TestJwt.forRole(Role.VETERINARIAN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void noTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/pets")).andExpect(status().isUnauthorized());
    }
}
