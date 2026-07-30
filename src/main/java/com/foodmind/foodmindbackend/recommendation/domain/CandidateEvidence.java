package com.foodmind.foodmindbackend.recommendation.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public record CandidateEvidence(
        UUID placeMealId,
        UUID mealId,
        String mealName,
        String mealType,
        String cuisineCode,
        UUID placeId,
        String placeName,
        String area,
        BigDecimal latitude,
        BigDecimal longitude,
        MoneyAmount price,
        Integer spiceLevel,
        boolean available,
        CleanlinessEvidence cleanliness,
        List<String> dietaryTagCodes,
        List<String> allergenCodes,
        boolean wantToTry,
        int personalRecordCount,
        BigDecimal personalAverageRating,
        OffsetDateTime lastPersonalRecordAt,
        int groupRecordCount,
        BigDecimal groupAverageRating,
        OffsetDateTime lastGroupRecordAt,
        BigDecimal distanceKm) {

    public CandidateEvidence {
        dietaryTagCodes = List.copyOf(dietaryTagCodes);
        allergenCodes = List.copyOf(allergenCodes);
    }
}
