package com.foodmind.foodmindbackend.media.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description: Immutable declared media metadata and its lifecycle state.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:35 pm
 */

public record MediaAsset(
        UUID id,
        UUID ownerUserId,
        String objectKey,
        String contentType,
        long byteSize,
        String checksumSha256,
        MediaAssetStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime finalisedAt,
        OffsetDateTime deletedAt) {
}
