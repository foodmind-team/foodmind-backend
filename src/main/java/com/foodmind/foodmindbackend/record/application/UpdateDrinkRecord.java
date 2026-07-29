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
public class UpdateDrinkRecord {

    private final DrinkRecordQuery drinkRecordQuery;
    private final GroupVisibilityValidator groupVisibilityValidator;
    private final Clock clock;

    public UpdateDrinkRecord(DrinkRecordQuery drinkRecordQuery, GroupVisibilityValidator groupVisibilityValidator, Clock clock) {
        this.drinkRecordQuery = drinkRecordQuery;
        this.groupVisibilityValidator = groupVisibilityValidator;
        this.clock = clock;
    }

    @Transactional
    public DrinkRecord handle(UUID ownerUserId, UUID id, long expectedVersion, Command command) {
        DrinkRecord current = drinkRecordQuery.findOwnerRecord(ownerUserId, id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        DrinkRecordValidation.validateExpectedVersion(current.version(), expectedVersion);

        FoodRecordVisibility visibility = command.visibility() == null ? current.visibility() : command.visibility();
        UUID groupId = visibility == FoodRecordVisibility.PRIVATE ? null : command.groupId() == null ? current.groupId() : command.groupId();
        UUID mediaAssetId = command.mediaAssetId() == null ? current.mediaAssetId() : command.mediaAssetId();
        BigDecimal price = command.price() == null ? current.price() : command.price();
        String currency = command.currency() == null ? current.currency() : command.currency();
        DrinkRecord updated = new DrinkRecord(
                current.id(),
                current.ownerUserId(),
                command.drinkName() == null ? current.drinkName() : command.drinkName().trim(),
                command.placeId() == null ? current.placeId() : command.placeId(),
                command.shopNameSnapshot() == null ? current.shopNameSnapshot() : command.shopNameSnapshot().trim(),
                command.occurredAt() == null ? current.occurredAt() : command.occurredAt(),
                price,
                normaliseCurrency(currency),
                command.rating() == null ? current.rating() : command.rating(),
                command.comment() == null ? current.comment() : trimToNull(command.comment()),
                command.sweetnessLevel() == null ? current.sweetnessLevel() : command.sweetnessLevel(),
                command.iceLevel() == null ? current.iceLevel() : command.iceLevel(),
                command.wouldBuyAgain() == null ? current.wouldBuyAgain() : command.wouldBuyAgain(),
                visibility,
                groupId,
                mediaAssetId,
                current.createdAt(),
                current.updatedAt(),
                current.version() + 1);
        DrinkRecordValidation.validate(
                updated.drinkName(),
                updated.shopNameSnapshot(),
                updated.occurredAt(),
                updated.price(),
                updated.currency(),
                updated.rating(),
                updated.comment(),
                updated.sweetnessLevel(),
                updated.iceLevel(),
                updated.visibility(),
                updated.groupId(),
                clock);
        groupVisibilityValidator.validateUpdate(ownerUserId, current.visibility(), current.groupId(), updated.visibility(), updated.groupId());
        validateReferences(ownerUserId, updated, current);
        return drinkRecordQuery.update(updated);
    }

    private void validateReferences(UUID ownerUserId, DrinkRecord updated, DrinkRecord current) {
        List<ApiFieldError> errors = new ArrayList<>();
        if (updated.placeId() != null && !updated.placeId().equals(current.placeId()) && !drinkRecordQuery.placeExists(updated.placeId())) {
            errors.add(new ApiFieldError("placeId", "UNKNOWN_REFERENCE", "Place was not found."));
        }
        if (updated.mediaAssetId() != null
                && !updated.mediaAssetId().equals(current.mediaAssetId())
                && !drinkRecordQuery.readyMediaExistsForOwner(ownerUserId, updated.mediaAssetId())) {
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
