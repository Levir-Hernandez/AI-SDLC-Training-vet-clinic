package com.vetclinic.catalog;

import com.vetclinic.catalog.dto.ServiceResponse;
import com.vetclinic.shared.error.NotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final ClinicServiceRepository services;

    public CatalogService(ClinicServiceRepository services) {
        this.services = services;
    }

    public List<ServiceResponse> list() {
        return services.findAll().stream().map(ServiceResponse::from).toList();
    }

    public ClinicService require(Long id) {
        return services.findById(id)
                .orElseThrow(() -> new NotFoundException("SERVICE_NOT_FOUND", "Service " + id + " not found"));
    }
}
