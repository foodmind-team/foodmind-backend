package com.foodmind.foodmindbackend.catalog.api;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The server-owned image registry for the curated catalogue.
 *
 * Keeping this mapping at the public API boundary means Web and Android receive
 * the same image reference instead of maintaining independent local fallbacks.
 */
public final class CuratedCatalogueImage {

    private static final String ROUTE_PREFIX = "/api/v1/catalogue-images/";
    private static final Map<UUID, String> RESOURCE_NAMES = Map.ofEntries(
            Map.entry(UUID.fromString("fb61151e-a29a-507b-91b5-09907116fc35"), "bistro-pasta.jpg"),
            Map.entry(UUID.fromString("ff90c8dc-7fe3-50c6-aaf0-8ea10f73c782"), "udon.jpg"),
            Map.entry(UUID.fromString("a032d001-48a7-517a-bef0-95bc39640bca"), "pastamania-pasta.jpg"),
            Map.entry(UUID.fromString("5801e48d-a6bb-5ab5-b3e7-93791ea05ada"), "burger-fries.jpg"),
            Map.entry(UUID.fromString("5ad2685a-0bad-528f-a067-54c3916fd7ac"), "syed-chicken-tikka.jpg"),
            Map.entry(UUID.fromString("92ec5b73-2358-548d-bbff-c8d5e4c49993"), "mei-black-glutinous-rice.jpg"),
            Map.entry(UUID.fromString("21000000-0000-4000-8000-000000000001"), "chicken-rice.jpg"),
            Map.entry(UUID.fromString("21000000-0000-4000-8000-000000000002"), "nasi-lemak.jpg"),
            Map.entry(UUID.fromString("21000000-0000-4000-8000-000000000003"), "serangoon-chana-masala.jpg"),
            Map.entry(UUID.fromString("21000000-0000-4000-8000-000000000004"), "tampines-chicken-rice.jpg"),
            Map.entry(UUID.fromString("23000000-0000-4000-8000-000000000001"), "soy-milk.jpg"),
            Map.entry(UUID.fromString("23000000-0000-4000-8000-000000000002"), "oatmeal.jpg"),
            Map.entry(UUID.fromString("23000000-0000-4000-8000-000000000003"), "roasted-peanuts.jpg"));

    private CuratedCatalogueImage() {
    }

    public static String referenceFor(UUID sourceId) {
        return resourceNameFor(sourceId).map(resourceName -> ROUTE_PREFIX + sourceId).orElse(null);
    }

    public static Optional<String> resourceNameFor(UUID sourceId) {
        return Optional.ofNullable(RESOURCE_NAMES.get(sourceId));
    }
}
