package com.foodmind.foodmindbackend.preference.domain;

import com.foodmind.foodmindbackend.common.api.ApiFieldError;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:55 pm
 */

public final class PreferenceValidation {

    private static final BigDecimal LATITUDE_MIN = new BigDecimal("-90");
    private static final BigDecimal LATITUDE_MAX = new BigDecimal("90");
    private static final BigDecimal LONGITUDE_MIN = new BigDecimal("-180");
    private static final BigDecimal LONGITUDE_MAX = new BigDecimal("180");
    private static final BigDecimal CLEANLINESS_MIN = BigDecimal.ZERO;
    private static final BigDecimal CLEANLINESS_MAX = BigDecimal.ONE;

    private PreferenceValidation() {
    }

    public static void validate(PreferenceReplacement replacement) {
        List<ApiFieldError> errors = new ArrayList<>();
        validateMoney(replacement, errors);
        validateRange("spiceTolerance", replacement.spiceTolerance(), 0, 5, errors);
        validateCoordinates(replacement, errors);
        validateRange("cleanlinessPriority", replacement.cleanlinessPriority(), 0, 5, errors);
        validateEvidence(replacement.minimumCleanlinessEvidenceScore(), errors);
        validateContradictions(replacement, errors);
        validateDuplicateAllergens(replacement, errors);
        if (!errors.isEmpty()) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    errors);
        }
    }

    private static void validateMoney(PreferenceReplacement replacement, List<ApiFieldError> errors) {
        if (replacement.budgetMin() != null && replacement.budgetMin().compareTo(BigDecimal.ZERO) < 0) {
            errors.add(new ApiFieldError("budgetMin", "POSITIVE_OR_ZERO", "Budget minimum must be zero or greater."));
        }
        if (replacement.budgetMax() != null && replacement.budgetMax().compareTo(BigDecimal.ZERO) < 0) {
            errors.add(new ApiFieldError("budgetMax", "POSITIVE_OR_ZERO", "Budget maximum must be zero or greater."));
        }
        if (replacement.budgetMin() != null
                && replacement.budgetMax() != null
                && replacement.budgetMin().compareTo(replacement.budgetMax()) > 0) {
            errors.add(new ApiFieldError("budgetMin", "RANGE_ORDER", "Budget minimum must be less than or equal to budget maximum."));
        }
        try {
            Currency.getInstance(replacement.currency());
        } catch (IllegalArgumentException exception) {
            errors.add(new ApiFieldError("currency", "ISO_CURRENCY", "Currency must be a supported ISO 4217 code."));
        }
    }

    private static void validateCoordinates(PreferenceReplacement replacement, List<ApiFieldError> errors) {
        boolean latitudePresent = replacement.preferredLatitude() != null;
        boolean longitudePresent = replacement.preferredLongitude() != null;
        if (latitudePresent != longitudePresent) {
            errors.add(new ApiFieldError("preferredLatitude", "COORDINATE_PAIR", "Preferred latitude and longitude must be supplied together."));
        }
        if (replacement.preferredLatitude() != null
                && (replacement.preferredLatitude().compareTo(LATITUDE_MIN) < 0
                || replacement.preferredLatitude().compareTo(LATITUDE_MAX) > 0)) {
            errors.add(new ApiFieldError("preferredLatitude", "RANGE", "Preferred latitude must be between -90 and 90."));
        }
        if (replacement.preferredLongitude() != null
                && (replacement.preferredLongitude().compareTo(LONGITUDE_MIN) < 0
                || replacement.preferredLongitude().compareTo(LONGITUDE_MAX) > 0)) {
            errors.add(new ApiFieldError("preferredLongitude", "RANGE", "Preferred longitude must be between -180 and 180."));
        }
        if (replacement.maxDistanceKm() != null) {
            if (replacement.maxDistanceKm().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(new ApiFieldError("maxDistanceKm", "POSITIVE", "Maximum distance must be greater than zero."));
            }
            if (!latitudePresent || !longitudePresent) {
                errors.add(new ApiFieldError("maxDistanceKm", "COORDINATE_REQUIRED", "Maximum distance requires preferred latitude and longitude."));
            }
        }
    }

    private static void validateEvidence(BigDecimal evidenceScore, List<ApiFieldError> errors) {
        if (evidenceScore != null
                && (evidenceScore.compareTo(CLEANLINESS_MIN) < 0 || evidenceScore.compareTo(CLEANLINESS_MAX) > 0)) {
            errors.add(new ApiFieldError("minimumCleanlinessEvidenceScore", "RANGE", "Minimum cleanliness evidence score must be between 0 and 1."));
        }
    }

    private static void validateRange(String field, Integer value, int min, int max, List<ApiFieldError> errors) {
        if (value != null && (value < min || value > max)) {
            errors.add(new ApiFieldError(field, "RANGE", "Value must be between %d and %d.".formatted(min, max)));
        }
    }

    private static void validateContradictions(PreferenceReplacement replacement, List<ApiFieldError> errors) {
        Set<String> liked = new HashSet<>(replacement.likedCuisineCodes());
        Set<String> disliked = new HashSet<>(replacement.dislikedCuisineCodes());
        liked.retainAll(disliked);
        if (!liked.isEmpty()) {
            errors.add(new ApiFieldError(
                    "likedCuisineCodes",
                    "CONTRADICTORY_CUISINE",
                    "Cuisine codes cannot appear in both likedCuisineCodes and dislikedCuisineCodes."));
        }
    }

    private static void validateDuplicateAllergens(PreferenceReplacement replacement, List<ApiFieldError> errors) {
        Set<String> seen = new HashSet<>();
        boolean duplicate = replacement.allergens().stream()
                .map(AllergenPreference::code)
                .anyMatch(code -> !seen.add(code));
        if (duplicate) {
            errors.add(new ApiFieldError("allergens", "DUPLICATE_REFERENCE_CODE", "Each allergen code may be supplied only once."));
        }
    }

    public static String normaliseCode(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
