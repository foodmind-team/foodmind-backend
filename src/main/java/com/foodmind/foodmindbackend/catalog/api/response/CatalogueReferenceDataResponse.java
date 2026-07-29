package com.foodmind.foodmindbackend.catalog.api.response;

import com.foodmind.foodmindbackend.catalog.domain.CatalogueReferenceData;
import com.foodmind.foodmindbackend.catalog.domain.ReferenceItem;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 9:36 pm
 */

public record CatalogueReferenceDataResponse(
        List<ReferenceItemResponse> cuisines,
        List<ReferenceItemResponse> dietaryTags,
        List<ReferenceItemResponse> allergens,
        List<String> mealTypes,
        List<String> placeTypes) {

    public static CatalogueReferenceDataResponse from(CatalogueReferenceData data) {
        return new CatalogueReferenceDataResponse(
                data.cuisines().stream().map(ReferenceItemResponse::from).toList(),
                data.dietaryTags().stream().map(ReferenceItemResponse::from).toList(),
                data.allergens().stream().map(ReferenceItemResponse::from).toList(),
                data.mealTypes(),
                data.placeTypes());
    }

    public record ReferenceItemResponse(UUID id, String code, String name) {

        static ReferenceItemResponse from(ReferenceItem item) {
            return new ReferenceItemResponse(item.id(), item.code(), item.name());
        }
    }
}
