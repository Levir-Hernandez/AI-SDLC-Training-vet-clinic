package com.vetclinic.owner;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vetclinic.auth.AccountService;
import com.vetclinic.auth.Role;
import com.vetclinic.owner.dto.CreateOwnerRequest;
import com.vetclinic.owner.dto.CreatePetRequest;
import com.vetclinic.owner.dto.OwnerResponse;
import com.vetclinic.owner.dto.PetResponse;
import com.vetclinic.shared.PageResponse;
import com.vetclinic.shared.error.ConflictException;
import com.vetclinic.shared.error.NotFoundException;

@Service
public class OwnerService {

	private final OwnerRepository owners;
	private final PetRepository pets;
	private final PetService petService;
	private final AccountService accounts;

	public OwnerService(OwnerRepository owners, PetRepository pets, PetService petService, AccountService accounts) {
		this.owners = owners;
		this.pets = pets;
		this.petService = petService;
		this.accounts = accounts;
	}

	@Transactional
	public OwnerResponse create(CreateOwnerRequest req) {
		if (owners.existsByPhone(req.phone())) {
			throw new ConflictException("PHONE_TAKEN", "Phone " + req.phone() + " is already registered");
		}
		Owner owner = owners.save(new Owner(req.name(), req.phone()));
		accounts.create(req.phone(), req.password(), Role.OWNER, req.name(), owner.getId(), null);
		List<PetResponse> created = new ArrayList<>();
		if (req.pets() != null) {
			for (CreatePetRequest p : req.pets()) {
				created.add(petService.add(owner, p));
			}
		}
		return OwnerResponse.from(owner, created);
	}

	@Transactional(readOnly = true)
	public PageResponse<OwnerResponse> list(String query, Pageable pageable) {
		String q = query == null ? "" : query.trim();
		return PageResponse.from(owners.search(q, pageable).map(o -> OwnerResponse.from(o, null)));
	}

	@Transactional(readOnly = true)
	public OwnerResponse get(Long id) {
		Owner owner = owners.findById(id)
			.orElseThrow(() -> new NotFoundException("OWNER_NOT_FOUND", "Owner " + id + " not found"));
		return OwnerResponse.from(owner, pets.findByOwnerId(id).stream().map(PetResponse::from).toList());
	}
}
