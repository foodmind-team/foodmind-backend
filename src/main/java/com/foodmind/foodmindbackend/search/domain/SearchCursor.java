package com.foodmind.foodmindbackend.search.domain;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import java.math.BigDecimal;
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

public record SearchCursor(BigDecimal relevance, OffsetDateTime sortAt, SearchSourceType sourceType, UUID sourceId) {

    public static SearchCursor after(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 4);
            if (parts.length != 4) {
                throw new IllegalArgumentException("Missing cursor parts.");
            }
            return new SearchCursor(
                    new BigDecimal(parts[0]),
                    OffsetDateTime.parse(parts[1]),
                    SearchSourceType.valueOf(parts[2]),
                    UUID.fromString(parts[3]));
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Search cursor is invalid.");
        }
    }

    public String encode() {
        String value = relevance.toPlainString() + "|" + sortAt + "|" + sourceType.name() + "|" + sourceId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
