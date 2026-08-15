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
        String categoryCode,
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
        boolean allergenEvidenceComplete,
        boolean wantToTry,
        int personalRecordCount,
        BigDecimal personalAverageRating,
        OffsetDateTime lastPersonalRecordAt,
        int groupRecordCount,
        BigDecimal groupAverageRating,
        OffsetDateTime lastGroupRecordAt,
        BigDecimal distanceKm,
        CandidateSourceType sourceType,
        UUID foodRecordId,
        String recordOwnerDisplayName,
        OffsetDateTime recordOccurredAt,
        boolean historicalPrice) {

    public CandidateEvidence {
        dietaryTagCodes = List.copyOf(dietaryTagCodes);
        allergenCodes = List.copyOf(allergenCodes);
        sourceType = sourceType == null ? CandidateSourceType.PLACE_MEAL : sourceType;
        if (sourceType == CandidateSourceType.PLACE_MEAL && placeMealId == null) {
            throw new IllegalArgumentException("placeMealId is required for PLACE_MEAL candidates");
        }
        if (sourceType == CandidateSourceType.FOOD_RECORD && foodRecordId == null) {
            throw new IllegalArgumentException("foodRecordId is required for FOOD_RECORD candidates");
        }
    }

    /** Compatibility constructor retained for existing catalogue-only callers and tests. */
    public CandidateEvidence(
            UUID placeMealId, UUID mealId, String mealName, String mealType, String cuisineCode,
            UUID placeId, String placeName, String area, BigDecimal latitude, BigDecimal longitude,
            MoneyAmount price, Integer spiceLevel, boolean available, CleanlinessEvidence cleanliness,
            List<String> dietaryTagCodes, List<String> allergenCodes, boolean wantToTry,
            int personalRecordCount, BigDecimal personalAverageRating, OffsetDateTime lastPersonalRecordAt,
            int groupRecordCount, BigDecimal groupAverageRating, OffsetDateTime lastGroupRecordAt,
            BigDecimal distanceKm) {
        this(placeMealId, mealId, mealName, mealType, mealType, cuisineCode, placeId, placeName, area, latitude,
                longitude, price, spiceLevel, available, cleanliness, dietaryTagCodes, allergenCodes, true,
                wantToTry,
                personalRecordCount, personalAverageRating, lastPersonalRecordAt, groupRecordCount,
                groupAverageRating, lastGroupRecordAt, distanceKm, CandidateSourceType.PLACE_MEAL, null, null,
                null, false);
    }

    public UUID sourceId() {
        return sourceType == CandidateSourceType.FOOD_RECORD ? foodRecordId : placeMealId;
    }

    public String sourceKey() {
        return sourceType.name() + ":" + sourceId();
    }
}
