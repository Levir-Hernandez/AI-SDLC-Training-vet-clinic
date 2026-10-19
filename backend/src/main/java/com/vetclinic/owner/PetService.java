package com.vetclinic.owner;

import com.vetclinic.auth.CurrentUser;
import com.vetclinic.auth.Role;
import com.vetclinic.owner.dto.CreatePetRequest;
import com.vetclinic.owner.dto.PetResponse;
import com.vetclinic.shared.PageResponse;
import com.vetclinic.shared.error.BadRequestException;
import com.vetclinic.shared.error.ForbiddenException;
import com.vetclinic.shared.error.NotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PetService {

    private final PetRepository pets;
    private final OwnerRepository owners;

    public PetService(PetRepository pets, OwnerRepository owners) {
        this.pets = pets;
        this.owners = owners;
    }

    @Transactional
    public PetResponse add(Long ownerId, CreatePetRequest request) {
        Owner owner = owners.findById(ownerId).orElseThrow(() ->
                new NotFoundException("OWNER_NOT_FOUND", "Owner " + ownerId + " not found"));
        // Pet constructor rejects a breed that does not belong to the species (BREED_SPECIES_MISMATCH)
        return PetResponse.from(pets.save(build(owner, request)));
    }

    /** Builds (and validates) a pet without persisting it, so callers can validate before saving anything. */
    Pet build(Owner owner, CreatePetRequest request) {
        return new Pet(request.name(), request.species(), request.breed(), owner);
    }

    /** TASK.md: owners see only their own pets; reception lists by owner; veterinarians have no access. */
    @Transactional(readOnly = true)
    public PageResponse<PetResponse> listFor(CurrentUser user, Long ownerId, Pageable pageable) {
        Long target = switch (user.role()) {
            case OWNER -> user.ownerId();
            case RECEPTIONIST -> {
                if (ownerId == null) {
                    throw new BadRequestException("OWNER_ID_REQUIRED", "ownerId is required");
                }
                yield ownerId;
            }
            case VETERINARIAN -> throw forbidden();
        };
        return PageResponse.from(pets.findByOwnerId(target, pageable).map(PetResponse::from));
    }

    @Transactional(readOnly = true)
    public Pet requireAccessible(CurrentUser user, Long petId) {
        if (user.role() == Role.VETERINARIAN) {
            throw forbidden();
        }
        Pet pet = pets.findById(petId).orElseThrow(() -> notFound(petId));
        if (user.role() == Role.OWNER && !pet.getOwner().getId().equals(user.ownerId())) {
            throw notFound(petId); // 404, not 403: do not reveal other owners' pets
        }
        return pet;
    }

    private NotFoundException notFound(Long petId) {
        return new NotFoundException("PET_NOT_FOUND", "Pet " + petId + " not found");
    }

    private ForbiddenException forbidden() {
        return new ForbiddenException("FORBIDDEN", "Not allowed to access pets");
    }
}
