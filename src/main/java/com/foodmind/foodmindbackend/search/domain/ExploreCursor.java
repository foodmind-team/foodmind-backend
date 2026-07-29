package com.foodmind.foodmindbackend.search.domain;

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
 * @date: 30/07/2026 02:01 am
 */

public record ExploreCursor(OffsetDateTime sortAt, SearchSourceType sourceType, UUID sourceId) {

    public static ExploreCursor after(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 3);
            if (parts.length != 3) {
                throw new IllegalArgumentException("Missing cursor parts.");
            }
            return new ExploreCursor(
                    OffsetDateTime.parse(parts[0]),
                    SearchSourceType.valueOf(parts[1]),
                    UUID.fromString(parts[2]));
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Explore cursor is invalid.");
        }
    }

    public String encode() {
        String value = sortAt + "|" + sourceType.name() + "|" + sourceId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
