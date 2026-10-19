package com.vetclinic.veterinarian;

import com.vetclinic.auth.AccountService;
import com.vetclinic.auth.Role;
import com.vetclinic.shared.PageResponse;
import com.vetclinic.shared.error.NotFoundException;
import com.vetclinic.veterinarian.dto.CreateVeterinarianRequest;
import com.vetclinic.veterinarian.dto.ScheduleBlockDto;
import com.vetclinic.veterinarian.dto.VeterinarianResponse;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VeterinarianService {

    private final VeterinarianRepository veterinarians;
    private final ScheduleValidator scheduleValidator;
    private final AccountService accounts;

    public VeterinarianService(VeterinarianRepository veterinarians, ScheduleValidator scheduleValidator,
                               AccountService accounts) {
        this.veterinarians = veterinarians;
        this.scheduleValidator = scheduleValidator;
        this.accounts = accounts;
    }

    /** Creates the veterinarian and the linked sign-in account in one transaction (A2). */
    @Transactional
    public VeterinarianResponse create(CreateVeterinarianRequest request) {
        List<ScheduleBlock> blocks = request.schedule().stream().map(ScheduleBlockDto::toBlock).toList();
        scheduleValidator.validate(blocks);
        Veterinarian vet = veterinarians.save(new Veterinarian(request.name(), request.specialization(),
                request.costPerService(), blocks));
        accounts.create(request.username(), request.password(), Role.VETERINARIAN, vet.getName(), null, vet.getId());
        return VeterinarianResponse.from(vet);
    }

    @Transactional(readOnly = true)
    public PageResponse<VeterinarianResponse> list(Pageable pageable) {
        return PageResponse.from(veterinarians.findAll(pageable).map(VeterinarianResponse::from));
    }

    @Transactional(readOnly = true)
    public VeterinarianResponse get(Long id) {
        return VeterinarianResponse.from(require(id));
    }

    @Transactional(readOnly = true)
    public Veterinarian require(Long id) {
        return veterinarians.findById(id).orElseThrow(() ->
                new NotFoundException("VETERINARIAN_NOT_FOUND", "Veterinarian " + id + " not found"));
    }
}
