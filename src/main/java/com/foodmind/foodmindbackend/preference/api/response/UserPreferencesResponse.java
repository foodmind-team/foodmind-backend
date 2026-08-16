package com.foodmind.foodmindbackend.preference.api.response;

import com.foodmind.foodmindbackend.preference.domain.AllergenPreference;
import com.foodmind.foodmindbackend.preference.domain.PreferenceSnapshot;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:55 pm
 */

public record UserPreferencesResponse(
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
        String cookingRegion,
        List<String> likedCuisineCodes,
        List<String> dislikedCuisineCodes,
        List<String> dietaryTagCodes,
        List<AllergenPreference> allergens,
        List<String> preferredMealTypes,
        HardConstraintSummary hardConstraints,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version) {

    public static UserPreferencesResponse from(PreferenceSnapshot snapshot) {
        return new UserPreferencesResponse(
                snapshot.budgetMin(),
                snapshot.budgetMax(),
                snapshot.currency(),
                snapshot.spiceTolerance(),
                snapshot.preferredArea(),
                snapshot.preferredLatitude(),
                snapshot.preferredLongitude(),
                snapshot.maxDistanceKm(),
                snapshot.cleanlinessPriority(),
                snapshot.minimumCleanlinessEvidenceScore(),
                snapshot.foodGoal(),
                snapshot.drinkSweetnessPreference(),
                snapshot.drinkIcePreference(),
                snapshot.cookingRegion(),
                snapshot.likedCuisineCodes(),
                snapshot.dislikedCuisineCodes(),
                snapshot.dietaryTagCodes(),
                snapshot.allergens(),
                snapshot.preferredMealTypes(),
                new HardConstraintSummary(snapshot.dietaryTagCodes(), snapshot.allergens()),
                snapshot.createdAt(),
                snapshot.updatedAt(),
                snapshot.version());
    }

    public record HardConstraintSummary(
            List<String> requiredDietaryTagCodes,
            List<AllergenPreference> allergens) {
    }
}
