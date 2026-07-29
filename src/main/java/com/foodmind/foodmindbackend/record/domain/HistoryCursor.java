package com.foodmind.foodmindbackend.record.domain;

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
 * @date: 30/07/2026 01:11 am
 */

public record HistoryCursor(
        OffsetDateTime occurredAt,
        HistorySourceType sourceType,
        UUID sourceId) {

    public static HistoryCursor after(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid cursor parts.");
            }
            return new HistoryCursor(
                    OffsetDateTime.parse(parts[0]),
                    HistorySourceType.valueOf(parts[1]),
                    UUID.fromString(parts[2]));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "History cursor is invalid.");
        }
    }

    public static String from(HistoryEntry entry) {
        String raw = entry.occurredAt() + "|" + entry.sourceType().name() + "|" + entry.sourceId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
