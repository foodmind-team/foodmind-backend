package com.foodmind.foodmindbackend.record.api.request;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.record.application.CreateDrinkRecord;
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
 * @date: 30/07/2026 01:11 am
 */

public record CreateDrinkRecordRequest(
        @NotBlank @Size(max = 160) String drinkName,
        UUID placeId,
        @NotBlank @Size(max = 160) String shopNameSnapshot,
        @NotNull OffsetDateTime occurredAt,
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @Size(min = 3, max = 3) String currency,
        @DecimalMin("1.0") @DecimalMax("5.0") @Digits(integer = 1, fraction = 1) BigDecimal rating,
        @Size(max = 4000) String comment,
        String sweetnessLevel,
        String iceLevel,
        Boolean wouldBuyAgain,
        FoodRecordVisibility visibility,
        UUID groupId,
        UUID mediaAssetId) {

    public CreateDrinkRecord.Command toCommand() {
        return new CreateDrinkRecord.Command(
                drinkName,
                placeId,
                shopNameSnapshot,
                occurredAt,
                price,
                currency,
                rating,
                comment,
                parseLevel(sweetnessLevel, "sweetnessLevel"),
                parseLevel(iceLevel, "iceLevel"),
                wouldBuyAgain,
                visibility,
                groupId,
                mediaAssetId);
    }

    private static Integer parseLevel(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException exception) {
            return switch (normalized) {
                case "NONE", "NO", "ZERO" -> 0;
                case "LOW", "LESS", "LIGHT" -> 1;
                case "MEDIUM", "NORMAL", "REGULAR" -> 3;
                case "HIGH", "MORE", "EXTRA", "FULL" -> 5;
                default -> throw new ApiException(ErrorCode.VALIDATION_ERROR, field + " must be 0-5 or a supported level label.");
            };
        }
    }
}
