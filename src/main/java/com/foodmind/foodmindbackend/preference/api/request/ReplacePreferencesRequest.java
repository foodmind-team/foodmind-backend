package com.foodmind.foodmindbackend.preference.api.request;

import com.foodmind.foodmindbackend.preference.domain.AllergenPreference;
import com.foodmind.foodmindbackend.preference.domain.PreferenceReplacement;
import com.foodmind.foodmindbackend.preference.domain.PreferenceValidation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:55 pm
 */

public record ReplacePreferencesRequest(
        BigDecimal budgetMin,
        BigDecimal budgetMax,
        @Pattern(regexp = "^[A-Za-z]{3}$") String currency,
        Integer spiceTolerance,
        @Size(max = 120) String preferredArea,
        BigDecimal preferredLatitude,
        BigDecimal preferredLongitude,
        BigDecimal maxDistanceKm,
        Integer cleanlinessPriority,
        BigDecimal minimumCleanlinessEvidenceScore,
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,39}$") String foodGoal,
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,19}$") String drinkSweetnessPreference,
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,19}$") String drinkIcePreference,
        @Pattern(regexp = "(?i)^(SG|US|CN)$") String cookingRegion,
        List<@Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,39}$") String> likedCuisineCodes,
        List<@Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,39}$") String> dislikedCuisineCodes,
        List<@Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,39}$") String> dietaryTagCodes,
        List<@Valid AllergenRequest> allergens,
        List<@Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,39}$") String> preferredMealTypes) {

    public PreferenceReplacement toReplacement() {
        return new PreferenceReplacement(
                budgetMin,
                budgetMax,
                normaliseCurrency(currency),
                spiceTolerance,
                clean(preferredArea),
                preferredLatitude,
                preferredLongitude,
                maxDistanceKm,
                cleanlinessPriority == null ? 0 : cleanlinessPriority,
                minimumCleanlinessEvidenceScore,
                PreferenceValidation.normaliseCode(foodGoal),
                PreferenceValidation.normaliseCode(drinkSweetnessPreference),
                PreferenceValidation.normaliseCode(drinkIcePreference),
                PreferenceValidation.normaliseCode(cookingRegion),
                normaliseCodes(likedCuisineCodes),
                normaliseCodes(dislikedCuisineCodes),
                normaliseCodes(dietaryTagCodes),
                allergenPreferences(),
                normaliseCodes(preferredMealTypes));
    }

    private List<AllergenPreference> allergenPreferences() {
        if (allergens == null) {
            return List.of();
        }
        return allergens.stream()
                .filter(Objects::nonNull)
                .map(allergen -> new AllergenPreference(
                        PreferenceValidation.normaliseCode(allergen.code()),
                        PreferenceValidation.normaliseCode(allergen.severity())))
                .distinct()
                .toList();
    }

    private List<String> normaliseCodes(List<String> codes) {
        if (codes == null) {
            return List.of();
        }
        return codes.stream()
                .filter(Objects::nonNull)
                .map(PreferenceValidation::normaliseCode)
                .distinct()
                .sorted()
                .toList();
    }

    private String normaliseCurrency(String currency) {
        String code = PreferenceValidation.normaliseCode(currency);
        return code == null ? "SGD" : code;
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    public record AllergenRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,39}$") String code,
            @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,19}$") String severity) {
    }
}
