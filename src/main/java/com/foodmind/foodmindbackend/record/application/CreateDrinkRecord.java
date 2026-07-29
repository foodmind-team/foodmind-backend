package com.foodmind.foodmindbackend.record.application;

import com.foodmind.foodmindbackend.common.api.ApiFieldError;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.record.application.port.DrinkRecordQuery;
import com.foodmind.foodmindbackend.record.domain.DrinkRecord;
import com.foodmind.foodmindbackend.record.domain.DrinkRecordValidation;
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
 * @date: 30/07/2026 01:11 am
 */

@Service
public class CreateDrinkRecord {

    private final DrinkRecordQuery drinkRecordQuery;
    private final GroupVisibilityValidator groupVisibilityValidator;
    private final Clock clock;

    public CreateDrinkRecord(DrinkRecordQuery drinkRecordQuery, GroupVisibilityValidator groupVisibilityValidator, Clock clock) {
        this.drinkRecordQuery = drinkRecordQuery;
        this.groupVisibilityValidator = groupVisibilityValidator;
        this.clock = clock;
    }

    @Transactional
    public DrinkRecord handle(UUID ownerUserId, Command command) {
        FoodRecordVisibility visibility = command.visibility() == null ? FoodRecordVisibility.PRIVATE : command.visibility();
        DrinkRecordValidation.validate(
                command.drinkName(),
                command.shopNameSnapshot(),
                command.occurredAt(),
                command.price(),
                command.currency(),
                command.rating(),
                command.comment(),
                command.sweetnessLevel(),
                command.iceLevel(),
                visibility,
                command.groupId(),
                clock);
        groupVisibilityValidator.validateCreate(ownerUserId, visibility, command.groupId());
        validateReferences(ownerUserId, command);
        DrinkRecord record = new DrinkRecord(
                UUID.randomUUID(),
                ownerUserId,
                trim(command.drinkName()),
                command.placeId(),
                trim(command.shopNameSnapshot()),
                command.occurredAt(),
                command.price(),
                normaliseCurrency(command.currency()),
                command.rating(),
                trimToNull(command.comment()),
                command.sweetnessLevel(),
                command.iceLevel(),
                command.wouldBuyAgain(),
                visibility,
                command.groupId(),
                command.mediaAssetId(),
                null,
                null,
                0L);
        return drinkRecordQuery.create(record);
    }

    private void validateReferences(UUID ownerUserId, Command command) {
        List<ApiFieldError> errors = new ArrayList<>();
        if (command.placeId() != null && !drinkRecordQuery.placeExists(command.placeId())) {
            errors.add(new ApiFieldError("placeId", "UNKNOWN_REFERENCE", "Place was not found."));
        }
        if (command.mediaAssetId() != null && !drinkRecordQuery.readyMediaExistsForOwner(ownerUserId, command.mediaAssetId())) {
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
            String drinkName,
            UUID placeId,
            String shopNameSnapshot,
            OffsetDateTime occurredAt,
            BigDecimal price,
            String currency,
            BigDecimal rating,
            String comment,
            Integer sweetnessLevel,
            Integer iceLevel,
            Boolean wouldBuyAgain,
            FoodRecordVisibility visibility,
            UUID groupId,
            UUID mediaAssetId) {
    }
}
