package com.vetclinic.owner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vetclinic.auth.Role;
import com.vetclinic.config.ClockConfig;
import com.vetclinic.config.SecurityConfig;
import com.vetclinic.owner.dto.OwnerResponse;
import com.vetclinic.owner.dto.PetResponse;
import com.vetclinic.shared.error.BadRequestException;
import com.vetclinic.shared.error.ConflictException;
import com.vetclinic.shared.error.GlobalExceptionHandler;
import com.vetclinic.support.TestJwt;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OwnerController.class)
@Import({SecurityConfig.class, ClockConfig.class, GlobalExceptionHandler.class})
class OwnerControllerTests {

    private static final String VALID = """
            {"name":"Maria","phone":"5550001","password":"maria123",
             "pets":[{"name":"Rex","species":"DOG","breed":"BULLDOG"}]}""";

    @Autowired MockMvc mockMvc;
    @MockitoBean OwnerService ownerService;
    @MockitoBean PetService petService;

    private String receptionist() {
        return "Bearer " + TestJwt.receptionist();
    }

    @Test
    void receptionistCreatesOwnerWith201AndLocation() throws Exception {
        when(ownerService.create(any())).thenReturn(new OwnerResponse(7L, "Maria", "5550001", List.of()));

        mockMvc.perform(post("/api/owners").header("Authorization", receptionist())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/owners/7"))
                .andExpect(jsonPath("$.name").value("Maria"));
    }

    @Test
    void missingNameIsRejectedWithFieldError() throws Exception {
        mockMvc.perform(post("/api/owners").header("Authorization", receptionist())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID.replace("\"Maria\"", "\"\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists());
        verifyNoInteractions(ownerService);
    }

    @Test
    void missingPhoneIsRejectedWithFieldError() throws Exception {
        mockMvc.perform(post("/api/owners").header("Authorization", receptionist())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID.replace("\"5550001\"", "\"\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'phone')]").exists());
    }

    @Test
    void duplicatePhoneReturns409() throws Exception {
        when(ownerService.create(any())).thenThrow(new ConflictException("PHONE_TAKEN", "Phone already registered"));

        mockMvc.perform(post("/api/owners").header("Authorization", receptionist())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHONE_TAKEN"));
    }

    @Test
    void addPetCreatesWith201() throws Exception {
        when(petService.add(any(), any())).thenReturn(
                new PetResponse(3L, "Tom", Species.CAT, Breed.SIAMESE, 7L));

        mockMvc.perform(post("/api/owners/7/pets").header("Authorization", receptionist())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tom\",\"species\":\"CAT\",\"breed\":\"SIAMESE\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/pets/3"));
    }

    @Test
    void breedNotMatchingSpeciesReturns400() throws Exception {
        when(petService.add(any(), any())).thenThrow(
                new BadRequestException("BREED_SPECIES_MISMATCH", "Breed SIAMESE is not a DOG breed"));

        mockMvc.perform(post("/api/owners/7/pets").header("Authorization", receptionist())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tom\",\"species\":\"DOG\",\"breed\":\"SIAMESE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BREED_SPECIES_MISMATCH"));
    }

    @Test
    void ownerTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/api/owners").header("Authorization", "Bearer " + TestJwt.forRole(Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());
    }

    @Test
    void noTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/owners").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized());
    }
}
