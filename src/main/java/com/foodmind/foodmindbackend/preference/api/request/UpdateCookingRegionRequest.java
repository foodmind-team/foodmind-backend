package com.foodmind.foodmindbackend.preference.api.request;

import com.foodmind.foodmindbackend.preference.domain.PreferenceValidation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateCookingRegionRequest(
        @NotBlank @Pattern(regexp = "(?i)^(SG|US|CN)$") String cookingRegion) {

    public String normalisedCookingRegion() {
        return PreferenceValidation.normaliseCode(cookingRegion);
    }
}
