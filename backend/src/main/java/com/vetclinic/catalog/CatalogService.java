package com.vetclinic.catalog;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vetclinic.catalog.dto.ServiceResponse;
import com.vetclinic.shared.error.NotFoundException;

@Service
public class CatalogService {

	private final ClinicServiceRepository services;

	public CatalogService(ClinicServiceRepository services) {
		this.services = services;
	}

	@Transactional(readOnly = true)
	public List<ServiceResponse> list() {
		return services.findAll().stream().map(ServiceResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public ClinicService require(Long id) {
		return services.findById(id)
			.orElseThrow(() -> new NotFoundException("SERVICE_NOT_FOUND", "Service " + id + " not found"));
	}
}
