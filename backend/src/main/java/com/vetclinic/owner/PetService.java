package com.vetclinic.owner;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vetclinic.auth.CurrentUser;
import com.vetclinic.auth.Role;
import com.vetclinic.owner.dto.CreatePetRequest;
import com.vetclinic.owner.dto.PetResponse;
import com.vetclinic.shared.PageResponse;
import com.vetclinic.shared.error.BadRequestException;
import com.vetclinic.shared.error.ForbiddenException;
import com.vetclinic.shared.error.NotFoundException;

@Service
public class PetService {

	private final PetRepository pets;
	private final OwnerRepository owners;

	public PetService(PetRepository pets, OwnerRepository owners) {
		this.pets = pets;
		this.owners = owners;
	}

	@Transactional
	public PetResponse add(Long ownerId, CreatePetRequest req) {
		Owner owner = owners.findById(ownerId)
			.orElseThrow(() -> new NotFoundException("OWNER_NOT_FOUND", "Owner " + ownerId + " not found"));
		return add(owner, req);
	}

	@Transactional
	public PetResponse add(Owner owner, CreatePetRequest req) {
		Pet pet = new Pet(req.name(), req.species(), req.breed(), owner);
		return PetResponse.from(pets.save(pet));
	}

	/** Owners see only their own pets (TASK.md data scoping); ownerId is ignored for them. */
	@Transactional(readOnly = true)
	public PageResponse<PetResponse> listFor(CurrentUser user, Long ownerId, Pageable pageable) {
		Long target;
		if (user.role() == Role.OWNER) {
			target = user.ownerId();
		}
		else if (user.role() == Role.RECEPTIONIST) {
			if (ownerId == null) {
				throw new BadRequestException("OWNER_ID_REQUIRED", "ownerId is required");
			}
			target = ownerId;
		}
		else {
			throw new ForbiddenException("FORBIDDEN", "Not allowed to list pets");
		}
		return PageResponse.from(pets.findByOwnerId(target, pageable).map(PetResponse::from));
	}

	@Transactional(readOnly = true)
	public Pet requireAccessible(CurrentUser user, Long petId) {
		if (user.role() == Role.VETERINARIAN) {
			throw new ForbiddenException("FORBIDDEN", "Not allowed to access pets");
		}
		Pet pet = pets.findById(petId)
			.orElseThrow(() -> new NotFoundException("PET_NOT_FOUND", "Pet " + petId + " not found"));
		if (user.role() == Role.OWNER && !pet.getOwner().getId().equals(user.ownerId())) {
			throw new NotFoundException("PET_NOT_FOUND", "Pet " + petId + " not found");
		}
		return pet;
	}
}
