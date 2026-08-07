package com.foodmind.foodmindbackend.recommendation.api.request;

import com.foodmind.foodmindbackend.recommendation.domain.RecommendationRequestContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public record GenerateRecommendationRequest(
        UUID parentSessionId,
        UUID groupId,
        @Size(max = 40) String mealType,
        @DecimalMin("0.0") BigDecimal maxBudget,
        @Size(min = 3, max = 3) String currency,
        @Size(max = 120) String area,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal maxDistanceKm,
        @Size(max = 120) String mood,
        @FutureOrPresent OffsetDateTime requestedFor,
        @Valid RecommendationConstraintsRequest constraints) {

    @AssertTrue(message = "maxBudget and currency must either both be provided or both be omitted")
    public boolean isBudgetCurrencyConsistent() {
        return (maxBudget == null) == (currency == null);
    }

    public RecommendationRequestContext toContext() {
        RecommendationConstraintsRequest safeConstraints = constraints == null
                ? new RecommendationConstraintsRequest(null, null, null, null)
                : constraints;
        return new RecommendationRequestContext(
                parentSessionId,
                groupId,
                mealType,
                maxBudget,
                currency,
                area,
                latitude,
                longitude,
                maxDistanceKm,
                mood,
                requestedFor,
                safeConstraints.avoidAllergenCodes(),
                safeConstraints.requiredDietaryTagCodes(),
                safeConstraints.maxSpiceLevel(),
                safeConstraints.minimumCleanlinessEvidenceScore());
    }
}
