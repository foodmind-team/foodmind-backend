package com.foodmind.foodmindbackend.preference.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:55 pm
 */

public record PreferenceReplacement(
        BigDecimal budgetMin,
        BigDecimal budgetMax,
        String currency,
        Integer spiceTolerance,
        String preferredArea,
        BigDecimal preferredLatitude,
        BigDecimal preferredLongitude,
        BigDecimal maxDistanceKm,
        Integer cleanlinessPriority,
        BigDecimal minimumCleanlinessEvidenceScore,
        String foodGoal,
        String drinkSweetnessPreference,
        String drinkIcePreference,
        List<String> likedCuisineCodes,
        List<String> dislikedCuisineCodes,
        List<String> dietaryTagCodes,
        List<AllergenPreference> allergens,
        List<String> preferredMealTypes) {

    public PreferenceReplacement {
        likedCuisineCodes = List.copyOf(likedCuisineCodes);
        dislikedCuisineCodes = List.copyOf(dislikedCuisineCodes);
        dietaryTagCodes = List.copyOf(dietaryTagCodes);
        allergens = List.copyOf(allergens);
        preferredMealTypes = List.copyOf(preferredMealTypes);
    }
}
