package com.vetclinic.appointment;

import com.vetclinic.appointment.dto.AppointmentResponse;
import com.vetclinic.auth.CurrentUser;
import com.vetclinic.shared.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Service history of a pet: reception for any pet, owners for their own pets only. */
@RestController
@RequestMapping("/api/pets")
public class PetHistoryController {

    private final AppointmentQueryService queries;

    public PetHistoryController(AppointmentQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('OWNER','RECEPTIONIST')")
    public PageResponse<AppointmentResponse> history(Authentication authentication, @PathVariable Long id,
                                                     @PageableDefault(size = 50) Pageable pageable) {
        return queries.historyForPet(CurrentUser.from(authentication), id, pageable);
    }
}
