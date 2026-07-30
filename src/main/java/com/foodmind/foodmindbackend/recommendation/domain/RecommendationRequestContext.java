package com.foodmind.foodmindbackend.recommendation.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public record RecommendationRequestContext(
        UUID parentSessionId,
        UUID groupId,
        String mealType,
        BigDecimal maxBudget,
        String currency,
        String area,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal maxDistanceKm,
        String mood,
        OffsetDateTime requestedFor,
        List<String> avoidAllergenCodes,
        List<String> requiredDietaryTagCodes,
        Integer maxSpiceLevel,
        BigDecimal minimumCleanlinessEvidenceScore) {

    public RecommendationRequestContext {
        mealType = normaliseCode(mealType);
        currency = normaliseCode(currency);
        area = trimToNull(area);
        mood = normaliseCode(mood);
        avoidAllergenCodes = normaliseCodes(avoidAllergenCodes);
        requiredDietaryTagCodes = normaliseCodes(requiredDietaryTagCodes);
    }

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    private static List<String> normaliseCodes(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(RecommendationRequestContext::normaliseCode)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private static String normaliseCode(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
