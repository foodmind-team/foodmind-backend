package com.foodmind.foodmindbackend.record.domain;

import com.foodmind.foodmindbackend.common.api.ApiFieldError;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 10:30 pm
 */

public final class FoodRecordValidation {

    private static final BigDecimal MIN_RATING = new BigDecimal("1.0");
    private static final BigDecimal MAX_RATING = new BigDecimal("5.0");
    private static final int MAX_COMMENT_LENGTH = 4000;
    private static final int MAX_NAME_LENGTH = 160;

    private FoodRecordValidation() {
    }

    public static void validate(
            String mealNameSnapshot,
            String placeNameSnapshot,
            OffsetDateTime occurredAt,
            BigDecimal price,
            String currency,
            BigDecimal rating,
            String comment,
            FoodRecordVisibility visibility,
            java.util.UUID groupId,
            Clock clock) {
        List<ApiFieldError> errors = new ArrayList<>();
        if (mealNameSnapshot == null || mealNameSnapshot.isBlank()) {
            errors.add(new ApiFieldError("mealNameSnapshot", "REQUIRED", "Meal name snapshot is required."));
        } else if (mealNameSnapshot.trim().length() > MAX_NAME_LENGTH) {
            errors.add(new ApiFieldError("mealNameSnapshot", "SIZE", "Meal name snapshot must be 160 characters or fewer."));
        }
        if (placeNameSnapshot != null && !placeNameSnapshot.isBlank() && placeNameSnapshot.trim().length() > MAX_NAME_LENGTH) {
            errors.add(new ApiFieldError("placeNameSnapshot", "SIZE", "Place name snapshot must be 160 characters or fewer."));
        }
        if (placeNameSnapshot != null && placeNameSnapshot.isBlank()) {
            errors.add(new ApiFieldError("placeNameSnapshot", "NOT_BLANK", "Place name snapshot must not be blank."));
        }
        if (occurredAt == null) {
            errors.add(new ApiFieldError("occurredAt", "REQUIRED", "Occurrence time is required."));
        } else if (occurredAt.isAfter(OffsetDateTime.now(clock).plusMinutes(5))) {
            errors.add(new ApiFieldError("occurredAt", "PAST_OR_PRESENT", "Occurrence time cannot be in the future."));
        }
        validateMoney(price, currency, errors);
        validateRating(rating, "rating", errors);
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            errors.add(new ApiFieldError("comment", "SIZE", "Comment must be 4000 characters or fewer."));
        }
        if (visibility == FoodRecordVisibility.GROUP) {
            errors.add(new ApiFieldError("visibility", "UNSUPPORTED_VISIBILITY", "Group food records are enabled by Branch 07."));
        }
        if (visibility == FoodRecordVisibility.PRIVATE && groupId != null) {
            errors.add(new ApiFieldError("groupId", "VISIBILITY_COMBINATION", "Private food records cannot include a groupId."));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), errors);
        }
    }

    public static void validateRating(BigDecimal rating, String field, List<ApiFieldError> errors) {
        if (rating == null) {
            return;
        }
        if (rating.compareTo(MIN_RATING) < 0 || rating.compareTo(MAX_RATING) > 0) {
            errors.add(new ApiFieldError(field, "RANGE", "Rating must be between 1.0 and 5.0."));
        }
        if (rating.scale() > 1) {
            errors.add(new ApiFieldError(field, "SCALE", "Rating may have at most one decimal place."));
        }
    }

    public static void validateExpectedVersion(long currentVersion, long expectedVersion) {
        if (currentVersion != expectedVersion) {
            throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Food record version does not match.");
        }
    }

    private static void validateMoney(BigDecimal price, String currency, List<ApiFieldError> errors) {
        boolean pricePresent = price != null;
        boolean currencyPresent = currency != null && !currency.isBlank();
        if (pricePresent != currencyPresent) {
            errors.add(new ApiFieldError("price", "MONEY_PAIR", "Price and currency must be supplied together."));
            return;
        }
        if (!pricePresent) {
            return;
        }
        if (price.compareTo(BigDecimal.ZERO) < 0 || price.scale() > 2) {
            errors.add(new ApiFieldError("price", "MONEY_AMOUNT", "Price must be zero or greater with at most two decimal places."));
        }
        try {
            Currency.getInstance(currency.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            errors.add(new ApiFieldError("currency", "ISO_CURRENCY", "Currency must be a supported ISO 4217 code."));
        }
    }
}
