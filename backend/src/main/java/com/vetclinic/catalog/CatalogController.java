package com.vetclinic.catalog;

import com.vetclinic.catalog.dto.ServiceResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/services")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    /** The service catalog is a closed list of four, so it is not paginated. */
    @GetMapping
    public List<ServiceResponse> list() {
        return catalog.list();
    }
}
