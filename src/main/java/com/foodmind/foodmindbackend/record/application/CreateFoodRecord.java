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
public class CreateFoodRecord {

    private final FoodRecordQuery foodRecordQuery;
    private final GroupVisibilityValidator groupVisibilityValidator;
    private final Clock clock;

    public CreateFoodRecord(FoodRecordQuery foodRecordQuery, GroupVisibilityValidator groupVisibilityValidator, Clock clock) {
        this.foodRecordQuery = foodRecordQuery;
        this.groupVisibilityValidator = groupVisibilityValidator;
        this.clock = clock;
    }

    @Transactional
    public FoodRecord create(UUID ownerUserId, Command command) {
        FoodRecordVisibility visibility = command.visibility() == null ? FoodRecordVisibility.PRIVATE : command.visibility();
        FoodRecordValidation.validate(
                command.mealNameSnapshot(),
                command.placeNameSnapshot(),
                command.occurredAt(),
                command.price(),
                command.currency(),
                command.rating(),
                command.comment(),
                visibility,
                command.groupId(),
                clock);
        groupVisibilityValidator.validateCreate(ownerUserId, visibility, command.groupId());
        validateReferences(ownerUserId, command);
        FoodRecord record = new FoodRecord(
                UUID.randomUUID(),
                ownerUserId,
                command.mealId(),
                trim(command.mealNameSnapshot()),
                command.placeId(),
                trimToNull(command.placeNameSnapshot()),
                command.cuisineId(),
                null,
                null,
                command.occurredAt(),
                command.price(),
                normaliseCurrency(command.currency()),
                command.rating(),
                trimToNull(command.comment()),
                command.wouldEatAgain(),
                visibility,
                command.groupId(),
                command.mediaAssetId(),
                null,
                null,
                0L);
        return foodRecordQuery.create(record);
    }

    private void validateReferences(UUID ownerUserId, Command command) {
        List<ApiFieldError> errors = new ArrayList<>();
        if (command.mealId() != null && !foodRecordQuery.mealExists(command.mealId())) {
            errors.add(new ApiFieldError("mealId", "UNKNOWN_REFERENCE", "Meal was not found."));
        }
        if (command.placeId() != null && !foodRecordQuery.placeExists(command.placeId())) {
            errors.add(new ApiFieldError("placeId", "UNKNOWN_REFERENCE", "Place was not found."));
        }
        if (command.cuisineId() != null && !foodRecordQuery.cuisineExists(command.cuisineId())) {
            errors.add(new ApiFieldError("cuisineId", "UNKNOWN_REFERENCE", "Cuisine was not found."));
        }
        if (command.mediaAssetId() != null && !foodRecordQuery.readyMediaExistsForOwner(ownerUserId, command.mediaAssetId())) {
            errors.add(new ApiFieldError("mediaAssetId", "MEDIA_NOT_READY", "Media asset must be owned by the user and READY."));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), errors);
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
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
