package com.foodmind.foodmindbackend.cooking.domain;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public record CookingPlanRequestContext(
        List<CookingPlanInput> ingredients,
        int servings,
        Integer maxMinutes,
        BigDecimal maxBudget,
        String currency,
        List<String> requiredDietaryTagCodes,
        List<String> avoidAllergenCodes) {

    private static final int MAX_INPUTS = 30;

    public CookingPlanRequestContext {
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        requiredDietaryTagCodes = normaliseCodes(requiredDietaryTagCodes);
        avoidAllergenCodes = normaliseCodes(avoidAllergenCodes);
        currency = currency == null ? null : currency.trim().toUpperCase(Locale.ROOT);
        validate(ingredients, servings, maxMinutes, maxBudget, currency);
    }

    private static List<String> normaliseCodes(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
    }

    private static void validate(
            List<CookingPlanInput> ingredients,
            int servings,
            Integer maxMinutes,
            BigDecimal maxBudget,
            String currency) {
        if (ingredients.isEmpty() || ingredients.size() > MAX_INPUTS) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Provide between 1 and 30 ordered ingredients.");
        }
        for (CookingPlanInput ingredient : ingredients) {
            if (ingredient.ingredientName() == null || ingredient.ingredientName().isBlank()
                    || ingredient.ingredientName().length() > 160) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Ingredient names must be non-blank and at most 160 characters.");
            }
            if ((ingredient.quantity() == null) != (ingredient.unit() == null)) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Ingredient quantity and unit must be supplied together.");
            }
            if (ingredient.quantity() != null && ingredient.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Ingredient quantities must be positive.");
            }
        }
        if (servings < 1 || servings > 24) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Servings must be between 1 and 24.");
        }
        if (maxMinutes != null && (maxMinutes < 1 || maxMinutes > 1440)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Max minutes must be between 1 and 1440.");
        }
        if ((maxBudget == null) != (currency == null)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Budget and currency must be supplied together.");
        }
        if (maxBudget != null && (maxBudget.compareTo(BigDecimal.ZERO) < 0 || currency.length() != 3)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Budget must be non-negative with a three-letter currency.");
        }
    }
}
