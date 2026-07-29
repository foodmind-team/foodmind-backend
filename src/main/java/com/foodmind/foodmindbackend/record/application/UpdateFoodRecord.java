package com.foodmind.foodmindbackend.record.application;

import com.foodmind.foodmindbackend.common.api.ApiFieldError;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.record.application.port.FoodRecordQuery;
import com.foodmind.foodmindbackend.record.domain.FoodRecord;
import com.foodmind.foodmindbackend.record.domain.FoodRecordValidation;
import com.foodmind.foodmindbackend.record.domain.FoodRecordVisibility;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 10:30 pm
 */

@Service
public class UpdateFoodRecord {

    private final FoodRecordQuery foodRecordQuery;
    private final GroupVisibilityValidator groupVisibilityValidator;
    private final Clock clock;

    public UpdateFoodRecord(FoodRecordQuery foodRecordQuery, GroupVisibilityValidator groupVisibilityValidator, Clock clock) {
        this.foodRecordQuery = foodRecordQuery;
        this.groupVisibilityValidator = groupVisibilityValidator;
        this.clock = clock;
    }

    @Transactional
    public FoodRecord update(UUID ownerUserId, UUID id, long expectedVersion, Command command) {
        FoodRecord current = foodRecordQuery.findOwnerRecord(ownerUserId, id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        FoodRecordValidation.validateExpectedVersion(current.version(), expectedVersion);

        FoodRecordVisibility visibility = command.visibility() == null ? current.visibility() : command.visibility();
        UUID groupId = visibility == FoodRecordVisibility.PRIVATE ? null : command.groupId() == null ? current.groupId() : command.groupId();
        UUID mediaAssetId = command.mediaAssetId() == null ? current.mediaAssetId() : command.mediaAssetId();
        BigDecimal price = command.price() == null ? current.price() : command.price();
        String currency = command.currency() == null ? current.currency() : command.currency();
        FoodRecord updated = new FoodRecord(
                current.id(),
                current.ownerUserId(),
                command.mealId() == null ? current.mealId() : command.mealId(),
                command.mealNameSnapshot() == null ? current.mealNameSnapshot() : command.mealNameSnapshot().trim(),
                command.placeId() == null ? current.placeId() : command.placeId(),
                command.placeNameSnapshot() == null ? current.placeNameSnapshot() : trimToNull(command.placeNameSnapshot()),
                command.cuisineId() == null ? current.cuisineId() : command.cuisineId(),
                current.cuisineCode(),
                current.cuisineName(),
                command.occurredAt() == null ? current.occurredAt() : command.occurredAt(),
                price,
                normaliseCurrency(currency),
                command.rating() == null ? current.rating() : command.rating(),
                command.comment() == null ? current.comment() : trimToNull(command.comment()),
                command.wouldEatAgain() == null ? current.wouldEatAgain() : command.wouldEatAgain(),
                visibility,
                groupId,
                mediaAssetId,
                current.createdAt(),
                current.updatedAt(),
                current.version() + 1);
        FoodRecordValidation.validate(
                updated.mealNameSnapshot(),
                updated.placeNameSnapshot(),
                updated.occurredAt(),
                updated.price(),
                updated.currency(),
                updated.rating(),
                updated.comment(),
                updated.visibility(),
                updated.groupId(),
                clock);
        groupVisibilityValidator.validateUpdate(ownerUserId, current, updated.visibility(), updated.groupId());
        validateReferences(ownerUserId, updated, current);
        return foodRecordQuery.update(updated);
    }

    private void validateReferences(UUID ownerUserId, FoodRecord updated, FoodRecord current) {
        List<ApiFieldError> errors = new ArrayList<>();
        if (updated.mealId() != null && !updated.mealId().equals(current.mealId()) && !foodRecordQuery.mealExists(updated.mealId())) {
            errors.add(new ApiFieldError("mealId", "UNKNOWN_REFERENCE", "Meal was not found."));
        }
        if (updated.placeId() != null && !updated.placeId().equals(current.placeId()) && !foodRecordQuery.placeExists(updated.placeId())) {
            errors.add(new ApiFieldError("placeId", "UNKNOWN_REFERENCE", "Place was not found."));
        }
        if (updated.cuisineId() != null && !updated.cuisineId().equals(current.cuisineId()) && !foodRecordQuery.cuisineExists(updated.cuisineId())) {
            errors.add(new ApiFieldError("cuisineId", "UNKNOWN_REFERENCE", "Cuisine was not found."));
        }
        if (updated.mediaAssetId() != null
                && !updated.mediaAssetId().equals(current.mediaAssetId())
                && !foodRecordQuery.readyMediaExistsForOwner(ownerUserId, updated.mediaAssetId())) {
            errors.add(new ApiFieldError("mediaAssetId", "MEDIA_NOT_READY", "Media asset must be owned by the user and READY."));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), errors);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normaliseCurrency(String currency) {
        return currency == null ? null : currency.trim().toUpperCase(Locale.ROOT);
    }

    public record Command(
            UUID mealId,
            String mealNameSnapshot,
            UUID placeId,
            String placeNameSnapshot,
            UUID cuisineId,
            OffsetDateTime occurredAt,
            BigDecimal price,
            String currency,
            BigDecimal rating,
            String comment,
            Boolean wouldEatAgain,
            FoodRecordVisibility visibility,
            UUID groupId,
            UUID mediaAssetId) {
    }
}
