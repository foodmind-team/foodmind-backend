package com.foodmind.foodmindbackend.catalog.domain;

import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 9:36 pm
 */

public record OfferingCandidate(
        UUID offeringId,
        UUID mealId,
        String mealName,
        String mealType,
        String cuisineCode,
        UUID placeId,
        String placeName,
        String area,
        Money price,
        Integer spiceLevel,
        CleanlinessEvidence cleanliness,
        List<String> dietaryTagCodes,
        List<String> allergenCodes) {

    public OfferingCandidate {
        dietaryTagCodes = List.copyOf(dietaryTagCodes);
        allergenCodes = List.copyOf(allergenCodes);
    }

    public record CleanlinessEvidence(java.math.BigDecimal score, java.time.OffsetDateTime observedAt, String sourceKind) {
    }
}
