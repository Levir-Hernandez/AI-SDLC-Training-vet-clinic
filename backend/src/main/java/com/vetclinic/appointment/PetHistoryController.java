package com.vetclinic.appointment;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vetclinic.appointment.dto.AppointmentResponse;
import com.vetclinic.auth.CurrentUser;
import com.vetclinic.shared.PageResponse;

@RestController
@RequestMapping("/api/pets/{id}/history")
public class PetHistoryController {

	private final AppointmentQueryService queries;

	public PetHistoryController(AppointmentQueryService queries) {
		this.queries = queries;
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('RECEPTIONIST','OWNER')")
	public PageResponse<AppointmentResponse> history(@PathVariable Long id, Pageable pageable,
			Authentication authentication) {
		return queries.historyForPet(CurrentUser.from(authentication), id, pageable);
	}
}
