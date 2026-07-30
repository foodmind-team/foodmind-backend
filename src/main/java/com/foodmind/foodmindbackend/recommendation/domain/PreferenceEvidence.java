package com.foodmind.foodmindbackend.recommendation.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public record PreferenceEvidence(
        BigDecimal budgetMax,
        String currency,
        Integer spiceTolerance,
        String preferredArea,
        BigDecimal preferredLatitude,
        BigDecimal preferredLongitude,
        BigDecimal maxDistanceKm,
        BigDecimal minimumCleanlinessEvidenceScore,
        List<String> likedCuisineCodes,
        List<String> dislikedCuisineCodes,
        List<String> dietaryTagCodes,
        List<String> allergenCodes,
        List<String> preferredMealTypes) {

    public PreferenceEvidence {
        likedCuisineCodes = List.copyOf(likedCuisineCodes);
        dislikedCuisineCodes = List.copyOf(dislikedCuisineCodes);
        dietaryTagCodes = List.copyOf(dietaryTagCodes);
        allergenCodes = List.copyOf(allergenCodes);
        preferredMealTypes = List.copyOf(preferredMealTypes);
    }
}
