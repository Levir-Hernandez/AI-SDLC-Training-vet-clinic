package com.vetclinic.owner;

import com.vetclinic.auth.AccountService;
import com.vetclinic.auth.Role;
import com.vetclinic.owner.dto.CreateOwnerRequest;
import com.vetclinic.owner.dto.CreatePetRequest;
import com.vetclinic.owner.dto.OwnerResponse;
import com.vetclinic.owner.dto.PetResponse;
import com.vetclinic.shared.PageResponse;
import com.vetclinic.shared.error.ConflictException;
import com.vetclinic.shared.error.NotFoundException;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerService {

    private final OwnerRepository owners;
    private final PetRepository pets;
    private final PetService petService;
    private final AccountService accounts;

    public OwnerService(OwnerRepository owners, PetRepository pets, PetService petService,
                        AccountService accounts) {
        this.owners = owners;
        this.pets = pets;
        this.petService = petService;
        this.accounts = accounts;
    }

    /** TASK.md: phone is unique; the phone is the owner's sign-in username (A1). */
    @Transactional
    public OwnerResponse create(CreateOwnerRequest request) {
        if (owners.existsByPhone(request.phone())) {
            throw new ConflictException("PHONE_TAKEN", "Phone " + request.phone() + " is already registered");
        }
        Owner owner = new Owner(request.name(), request.phone());
        // Build and validate every pet before persisting anything.
        List<CreatePetRequest> petRequests = request.pets() == null ? List.of() : request.pets();
        List<Pet> newPets = petRequests.stream().map(p -> petService.build(owner, p)).toList();

        owners.save(owner);
        accounts.create(request.phone(), request.password(), Role.OWNER, owner.getName(), owner.getId(), null);
        List<PetResponse> saved = pets.saveAll(newPets).stream().map(PetResponse::from).toList();
        return OwnerResponse.from(owner, saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<OwnerResponse> list(String query, Pageable pageable) {
        String q = query == null || query.isBlank() ? null : query.trim();
        // List rows omit pets; the detail view includes them.
        return PageResponse.from(owners.search(q, pageable).map(o -> OwnerResponse.from(o, List.of())));
    }

    @Transactional(readOnly = true)
    public OwnerResponse get(Long id) {
        Owner owner = owners.findById(id).orElseThrow(() ->
                new NotFoundException("OWNER_NOT_FOUND", "Owner " + id + " not found"));
        List<PetResponse> ownerPets = pets.findByOwnerIdOrderById(id).stream().map(PetResponse::from).toList();
        return OwnerResponse.from(owner, ownerPets);
    }
}
