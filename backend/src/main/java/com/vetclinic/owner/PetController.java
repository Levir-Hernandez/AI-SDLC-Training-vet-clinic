package com.vetclinic.owner;

import com.vetclinic.auth.CurrentUser;
import com.vetclinic.owner.dto.PetResponse;
import com.vetclinic.shared.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pets")
public class PetController {

    private final PetService pets;

    public PetController(PetService pets) {
        this.pets = pets;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','RECEPTIONIST')")
    public PageResponse<PetResponse> list(Authentication authentication,
                                          @RequestParam(required = false) Long ownerId,
                                          @PageableDefault(size = 50, sort = "name") Pageable pageable) {
        return pets.listFor(CurrentUser.from(authentication), ownerId, pageable);
    }
}
