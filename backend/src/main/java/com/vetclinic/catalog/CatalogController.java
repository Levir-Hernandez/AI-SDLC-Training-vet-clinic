package com.vetclinic.catalog;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vetclinic.catalog.dto.ServiceResponse;

@RestController
@RequestMapping("/api/services")
public class CatalogController {

	private final CatalogService catalog;

	public CatalogController(CatalogService catalog) {
		this.catalog = catalog;
	}

	@GetMapping
	public List<ServiceResponse> list() {
		return catalog.list();
	}
}
