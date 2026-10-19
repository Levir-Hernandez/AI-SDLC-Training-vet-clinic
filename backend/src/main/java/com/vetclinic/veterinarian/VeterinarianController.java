package com.vetclinic.veterinarian;

import com.vetclinic.shared.PageResponse;
import com.vetclinic.veterinarian.dto.CreateVeterinarianRequest;
import com.vetclinic.veterinarian.dto.VeterinarianResponse;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/veterinarians")
public class VeterinarianController {

    private final VeterinarianService veterinarians;

    public VeterinarianController(VeterinarianService veterinarians) {
        this.veterinarians = veterinarians;
    }

    @GetMapping
    public PageResponse<VeterinarianResponse> list(@PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return veterinarians.list(pageable);
    }

    @GetMapping("/{id}")
    public VeterinarianResponse get(@PathVariable Long id) {
        return veterinarians.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<VeterinarianResponse> create(@Valid @RequestBody CreateVeterinarianRequest request) {
        VeterinarianResponse created = veterinarians.create(request);
        return ResponseEntity.created(URI.create("/api/veterinarians/" + created.id())).body(created);
    }
}
