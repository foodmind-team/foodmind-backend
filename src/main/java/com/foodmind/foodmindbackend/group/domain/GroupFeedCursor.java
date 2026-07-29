package com.foodmind.foodmindbackend.group.domain;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

public record GroupFeedCursor(OffsetDateTime occurredAt, FeedSourceType sourceType, UUID sourceId) {

    public static GroupFeedCursor after(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 3);
            return new GroupFeedCursor(
                    OffsetDateTime.parse(parts[0]),
                    FeedSourceType.valueOf(parts[1]),
                    UUID.fromString(parts[2]));
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Feed cursor is invalid.");
        }
    }

    public String encode() {
        String value = occurredAt + "|" + sourceType.name() + "|" + sourceId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
