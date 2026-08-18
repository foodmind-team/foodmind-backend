package com.foodmind.foodmindbackend.catalog.api;

import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Serves only the small, non-sensitive curated catalogue image set. */
@RestController
@RequestMapping("/api/v1/catalogue-images")
public class CatalogueImageController {

    @GetMapping(value = "/{sourceId}", produces = MediaType.IMAGE_JPEG_VALUE)
    ResponseEntity<Resource> image(@PathVariable UUID sourceId) {
        return CuratedCatalogueImage.resourceNameFor(sourceId)
                .<ResponseEntity<Resource>>map(resourceName -> ResponseEntity.ok()
                        // A changed catalogue response must be visible to either client immediately.
                        .cacheControl(CacheControl.noStore())
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(new ClassPathResource("catalogue-images/" + resourceName)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
