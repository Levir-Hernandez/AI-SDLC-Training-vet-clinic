package com.vetclinic.owner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.vetclinic.config.SecurityConfig;
import com.vetclinic.owner.dto.PetResponse;
import com.vetclinic.shared.PageResponse;
import com.vetclinic.shared.error.GlobalExceptionHandler;
import com.vetclinic.support.TestJwt;

@WebMvcTest(PetController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class })
class PetControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PetService petService;

	@Test
	void ownerListsPets() throws Exception {
		when(petService.listFor(any(), isNull(), any())).thenReturn(new PageResponse<>(
				List.of(new PetResponse(1L, "Rex", Species.DOG, Breed.BULLDOG, 1L)), 0, 20, 1));

		mockMvc.perform(get("/api/pets").header("Authorization", "Bearer " + TestJwt.owner(1L)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].name").value("Rex"));
	}

	@Test
	void veterinarianCannotListPets() throws Exception {
		mockMvc.perform(get("/api/pets").header("Authorization", "Bearer " + TestJwt.veterinarian(1L)))
			.andExpect(status().isForbidden());
	}
}
