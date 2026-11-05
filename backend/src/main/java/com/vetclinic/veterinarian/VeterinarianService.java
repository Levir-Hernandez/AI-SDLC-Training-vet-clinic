package com.vetclinic.veterinarian;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vetclinic.auth.AccountService;
import com.vetclinic.auth.Role;
import com.vetclinic.shared.PageResponse;
import com.vetclinic.shared.error.NotFoundException;
import com.vetclinic.veterinarian.dto.CreateVeterinarianRequest;
import com.vetclinic.veterinarian.dto.ScheduleBlockDto;
import com.vetclinic.veterinarian.dto.VeterinarianResponse;

@Service
public class VeterinarianService {

	private final VeterinarianRepository vets;
	private final ScheduleValidator scheduleValidator;
	private final AccountService accounts;

	public VeterinarianService(VeterinarianRepository vets, ScheduleValidator scheduleValidator,
			AccountService accounts) {
		this.vets = vets;
		this.scheduleValidator = scheduleValidator;
		this.accounts = accounts;
	}

	@Transactional
	public VeterinarianResponse create(CreateVeterinarianRequest req) {
		var blocks = req.schedule().stream().map(ScheduleBlockDto::toEntity).toList();
		scheduleValidator.validate(blocks);
		Veterinarian vet = vets.save(new Veterinarian(req.name(), req.specialization(), req.costPerService(), blocks));
		accounts.create(req.username(), req.password(), Role.VETERINARIAN, req.name(), null, vet.getId());
		return VeterinarianResponse.from(vet);
	}

	@Transactional(readOnly = true)
	public PageResponse<VeterinarianResponse> list(Pageable pageable) {
		return PageResponse.from(vets.findAll(pageable).map(VeterinarianResponse::from));
	}

	@Transactional(readOnly = true)
	public VeterinarianResponse get(Long id) {
		return VeterinarianResponse.from(require(id));
	}

	@Transactional(readOnly = true)
	public Veterinarian require(Long id) {
		return vets.findById(id)
			.orElseThrow(() -> new NotFoundException("VETERINARIAN_NOT_FOUND", "Veterinarian " + id + " not found"));
	}
}
