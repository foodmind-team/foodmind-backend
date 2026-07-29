package com.foodmind.foodmindbackend.record.api.request;

import com.foodmind.foodmindbackend.record.application.CreateFoodRecord;
import com.foodmind.foodmindbackend.record.domain.FoodRecordVisibility;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 10:30 pm
 */

public record CreateFoodRecordRequest(
        UUID mealId,
        @NotBlank @Size(max = 160) String mealNameSnapshot,
        UUID placeId,
        @Size(max = 160) String placeNameSnapshot,
        UUID cuisineId,
        @NotNull OffsetDateTime occurredAt,
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @Size(min = 3, max = 3) String currency,
        @DecimalMin("1.0") @DecimalMax("5.0") @Digits(integer = 1, fraction = 1) BigDecimal rating,
        @Size(max = 4000) String comment,
        Boolean wouldEatAgain,
        FoodRecordVisibility visibility,
        UUID groupId,
        UUID mediaAssetId) {

    public CreateFoodRecord.Command toCommand() {
        return new CreateFoodRecord.Command(
                mealId,
                mealNameSnapshot,
                placeId,
                placeNameSnapshot,
                cuisineId,
                occurredAt,
                price,
                currency,
                rating,
                comment,
                wouldEatAgain,
                visibility,
                groupId,
                mediaAssetId);
    }
}
