package com.foodmind.foodmindbackend.user.application;

import com.foodmind.foodmindbackend.common.api.ApiFieldError;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.user.application.port.UserAccountRepository;
import com.foodmind.foodmindbackend.user.domain.User;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:55 pm
 */

@Service
public class UpdateCurrentUser {

    private final UserAccountRepository userAccountRepository;

    public UpdateCurrentUser(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public User update(UUID userId, String displayName, String timeZone) {
        User current = userAccountRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTHENTICATION_REQUIRED));

        String nextDisplayName = displayName == null ? current.displayName() : cleanDisplayName(displayName);
        String nextTimeZone = timeZone == null ? current.timeZone() : validateTimeZone(timeZone);

        return userAccountRepository.save(new User(
                current.id(),
                current.email(),
                current.normalisedEmail(),
                current.passwordHash(),
                nextDisplayName,
                current.role(),
                current.status(),
                nextTimeZone,
                current.createdAt(),
                current.updatedAt(),
                current.lastLoginAt(),
                current.deactivatedAt(),
                current.version()));
    }

    private String cleanDisplayName(String displayName) {
        String cleaned = displayName.trim();
        if (cleaned.isBlank()) {
            throw validation("displayName", "NOT_BLANK", "Display name is required.");
        }
        return cleaned;
    }

    private String validateTimeZone(String timeZone) {
        String cleaned = timeZone.trim();
        if (cleaned.isBlank()) {
            throw validation("timeZone", "NOT_BLANK", "Timezone is required.");
        }
        try {
            return ZoneId.of(cleaned).getId();
        } catch (DateTimeException exception) {
            throw validation("timeZone", "INVALID_TIME_ZONE", "Timezone must be a valid IANA timezone identifier.");
        }
    }

    private ApiException validation(String field, String code, String message) {
        return new ApiException(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                List.of(new ApiFieldError(field, code, message)));
    }
}
