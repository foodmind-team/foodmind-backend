package com.foodmind.foodmindbackend.preference.api.internal.response;

import com.foodmind.foodmindbackend.preference.domain.AllergenPreference;
import com.foodmind.foodmindbackend.preference.domain.PreferenceSnapshot;
import java.math.BigDecimal;
import java.util.List;

/** Minimal preference projection needed by the delegated chatbot. */
public record InternalProfileResponse(
        BigDecimal budgetMin,
        BigDecimal budgetMax,
        String currency,
        Integer spiceTolerance,
        String preferredArea,
        BigDecimal maxDistanceKm,
        String foodGoal,
        String drinkSweetnessPreference,
        String drinkIcePreference,
        String cookingRegion,
        List<String> likedCuisineCodes,
        List<String> dislikedCuisineCodes,
        List<String> dietaryTagCodes,
        List<AllergenPreference> allergens,
        List<String> preferredMealTypes) {

    public static InternalProfileResponse from(PreferenceSnapshot snapshot) {
        return new InternalProfileResponse(
                snapshot.budgetMin(),
                snapshot.budgetMax(),
                snapshot.currency(),
                snapshot.spiceTolerance(),
                snapshot.preferredArea(),
                snapshot.maxDistanceKm(),
                snapshot.foodGoal(),
                snapshot.drinkSweetnessPreference(),
                snapshot.drinkIcePreference(),
                snapshot.cookingRegion(),
                snapshot.likedCuisineCodes(),
                snapshot.dislikedCuisineCodes(),
                snapshot.dietaryTagCodes(),
                snapshot.allergens(),
                snapshot.preferredMealTypes());
    }
}
