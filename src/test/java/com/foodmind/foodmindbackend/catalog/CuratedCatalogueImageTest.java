package com.foodmind.foodmindbackend.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.foodmind.foodmindbackend.catalog.api.CuratedCatalogueImage;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CuratedCatalogueImageTest {

    @Test
    void assignsEachSupportedCatalogueItemOneDistinctBackendResource() {
        List<UUID> sourceIds = List.of(
                UUID.fromString("fb61151e-a29a-507b-91b5-09907116fc35"),
                UUID.fromString("ff90c8dc-7fe3-50c6-aaf0-8ea10f73c782"),
                UUID.fromString("a032d001-48a7-517a-bef0-95bc39640bca"),
                UUID.fromString("5801e48d-a6bb-5ab5-b3e7-93791ea05ada"),
                UUID.fromString("5ad2685a-0bad-528f-a067-54c3916fd7ac"),
                UUID.fromString("92ec5b73-2358-548d-bbff-c8d5e4c49993"),
                UUID.fromString("21000000-0000-4000-8000-000000000001"),
                UUID.fromString("21000000-0000-4000-8000-000000000002"),
                UUID.fromString("21000000-0000-4000-8000-000000000003"),
                UUID.fromString("21000000-0000-4000-8000-000000000004"),
                UUID.fromString("23000000-0000-4000-8000-000000000001"),
                UUID.fromString("23000000-0000-4000-8000-000000000002"),
                UUID.fromString("23000000-0000-4000-8000-000000000003"));

        assertThat(sourceIds.stream().map(CuratedCatalogueImage::resourceNameFor).flatMap(java.util.Optional::stream).toList())
                .hasSize(sourceIds.size())
                .doesNotHaveDuplicates();
        assertThat(CuratedCatalogueImage.referenceFor(sourceIds.get(0)))
                .isEqualTo("/api/v1/catalogue-images/" + sourceIds.get(0));
        assertThat(CuratedCatalogueImage.referenceFor(UUID.randomUUID())).isNull();
    }
}
