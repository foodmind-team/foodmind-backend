package com.foodmind.foodmindbackend.catalog.domain;

import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 9:36 pm
 */

public record CatalogueReferenceData(
        List<ReferenceItem> cuisines,
        List<ReferenceItem> dietaryTags,
        List<ReferenceItem> allergens,
        List<String> mealTypes,
        List<String> placeTypes) {

    public CatalogueReferenceData {
        cuisines = List.copyOf(cuisines);
        dietaryTags = List.copyOf(dietaryTags);
        allergens = List.copyOf(allergens);
        mealTypes = List.copyOf(mealTypes);
        placeTypes = List.copyOf(placeTypes);
    }
}
