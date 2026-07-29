package com.foodmind.foodmindbackend.catalog.api;

import com.foodmind.foodmindbackend.catalog.api.response.CatalogueMealResponse;
import com.foodmind.foodmindbackend.catalog.api.response.CataloguePlaceResponse;
import com.foodmind.foodmindbackend.catalog.api.response.CatalogueProductResponse;
import com.foodmind.foodmindbackend.catalog.api.response.CatalogueReferenceDataResponse;
import com.foodmind.foodmindbackend.catalog.application.CatalogueService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 9:36 pm
 */

@RestController
@RequestMapping("/api/v1/catalogue")
public class CatalogueController {

    private final CatalogueService catalogueService;

    public CatalogueController(CatalogueService catalogueService) {
        this.catalogueService = catalogueService;
    }

    @GetMapping("/reference-data")
    CatalogueReferenceDataResponse referenceData() {
        return CatalogueReferenceDataResponse.from(catalogueService.referenceData());
    }

    @GetMapping("/meals/{id}")
    CatalogueMealResponse meal(@PathVariable UUID id) {
        return CatalogueMealResponse.from(catalogueService.meal(id));
    }

    @GetMapping("/places/{id}")
    CataloguePlaceResponse place(@PathVariable UUID id) {
        return CataloguePlaceResponse.from(catalogueService.place(id));
    }

    @GetMapping("/products/{id}")
    CatalogueProductResponse product(@PathVariable UUID id) {
        return CatalogueProductResponse.from(catalogueService.product(id));
    }
}
