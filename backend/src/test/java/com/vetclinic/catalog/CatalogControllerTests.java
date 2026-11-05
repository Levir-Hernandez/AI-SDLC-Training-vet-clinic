package com.vetclinic.catalog;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.vetclinic.catalog.dto.ServiceResponse;
import com.vetclinic.config.SecurityConfig;
import com.vetclinic.shared.error.GlobalExceptionHandler;
import com.vetclinic.support.TestJwt;

@WebMvcTest(CatalogController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class })
class CatalogControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CatalogService catalogService;

	@Test
	void returnsServicesFromCatalog() throws Exception {
		ServiceCode code = ServiceCode.values()[0];
		when(catalogService.list()).thenReturn(List.of(new ServiceResponse(1L, code, "Consult", 30, new BigDecimal("40.00"))));

		mockMvc.perform(get("/api/services").header("Authorization", "Bearer " + TestJwt.owner(1L)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].name").value("Consult"))
			.andExpect(jsonPath("$[0].durationMinutes").value(30));
	}

	@Test
	void withoutTokenReturns401() throws Exception {
		mockMvc.perform(get("/api/services")).andExpect(status().isUnauthorized());
	}
}
