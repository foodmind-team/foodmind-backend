package com.foodmind.foodmindbackend.recommendation.api.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public record RecommendationConstraintsRequest(
        List<String> avoidAllergenCodes,
        List<String> requiredDietaryTagCodes,
        @Min(0) @Max(5) Integer maxSpiceLevel,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal minimumCleanlinessEvidenceScore) {
}
