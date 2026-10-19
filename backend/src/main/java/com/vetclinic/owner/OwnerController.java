package com.vetclinic.owner;

import com.vetclinic.owner.dto.CreateOwnerRequest;
import com.vetclinic.owner.dto.CreatePetRequest;
import com.vetclinic.owner.dto.OwnerResponse;
import com.vetclinic.owner.dto.PetResponse;
import com.vetclinic.shared.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/owners")
@PreAuthorize("hasRole('RECEPTIONIST')")
public class OwnerController {

    private final OwnerService owners;
    private final PetService pets;

    public OwnerController(OwnerService owners, PetService pets) {
        this.owners = owners;
        this.pets = pets;
    }

    @PostMapping
    public ResponseEntity<OwnerResponse> create(@Valid @RequestBody CreateOwnerRequest request) {
        OwnerResponse created = owners.create(request);
        return ResponseEntity.created(URI.create("/api/owners/" + created.id())).body(created);
    }

    @GetMapping
    public PageResponse<OwnerResponse> list(@RequestParam(required = false) String q,
                                            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return owners.list(q, pageable);
    }

    @GetMapping("/{id}")
    public OwnerResponse get(@PathVariable Long id) {
        return owners.get(id);
    }

    @PostMapping("/{id}/pets")
    public ResponseEntity<PetResponse> addPet(@PathVariable Long id, @Valid @RequestBody CreatePetRequest request) {
        PetResponse created = pets.add(id, request);
        return ResponseEntity.created(URI.create("/api/pets/" + created.id())).body(created);
    }
}
