package com.foodmind.foodmindbackend.group.domain;

import com.foodmind.foodmindbackend.common.api.ApiFieldError;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

public final class GroupValidation {

    public static final int MAX_NAME_LENGTH = 120;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;
    public static final int MAX_MESSAGE_LENGTH = 2000;

    private GroupValidation() {
    }

    public static void validateGroup(String name, String description) {
        List<ApiFieldError> errors = new ArrayList<>();
        if (name == null || name.isBlank()) {
            errors.add(new ApiFieldError("name", "REQUIRED", "Group name is required."));
        } else if (name.trim().length() > MAX_NAME_LENGTH) {
            errors.add(new ApiFieldError("name", "SIZE", "Group name must be 120 characters or fewer."));
        }
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            errors.add(new ApiFieldError("description", "SIZE", "Description must be 2000 characters or fewer."));
        }
        throwIfErrors(errors);
    }

    public static void validateInvitation(Duration ttl, int maxUses) {
        List<ApiFieldError> errors = new ArrayList<>();
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            errors.add(new ApiFieldError("expiresInHours", "POSITIVE", "Invitation expiry must be in the future."));
        } else if (ttl.toDays() > 30) {
            errors.add(new ApiFieldError("expiresInHours", "MAX", "Invitation expiry must be 30 days or less."));
        }
        if (maxUses < 1 || maxUses > 100) {
            errors.add(new ApiFieldError("maxUses", "RANGE", "Max uses must be between 1 and 100."));
        }
        throwIfErrors(errors);
    }

    public static void validateShareMessage(String message) {
        if (message != null && message.length() > MAX_MESSAGE_LENGTH) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    List.of(new ApiFieldError("message", "SIZE", "Message must be 2000 characters or fewer.")));
        }
    }

    public static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static void throwIfErrors(List<ApiFieldError> errors) {
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), errors);
        }
    }
}
