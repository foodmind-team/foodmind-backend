package com.foodmind.foodmindbackend.cooking.domain;

import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public record CookingPreferenceRules(
        List<String> requiredDietaryTagCodes,
        List<String> avoidAllergenCodes) {

    public CookingPreferenceRules {
        requiredDietaryTagCodes = requiredDietaryTagCodes == null ? List.of() : List.copyOf(requiredDietaryTagCodes);
        avoidAllergenCodes = avoidAllergenCodes == null ? List.of() : List.copyOf(avoidAllergenCodes);
    }
}
