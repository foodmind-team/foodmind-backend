package com.foodmind.foodmindbackend.media.api.response;

import com.foodmind.foodmindbackend.media.domain.model.MediaAsset;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description: Safe owner-visible media lifecycle response without object keys.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:35 pm
 */

public record MediaAssetResponse(UUID mediaAssetId, String status, String contentType, long byteSize,
        OffsetDateTime createdAt, OffsetDateTime finalisedAt) {
    public static MediaAssetResponse from(MediaAsset asset) {
        return new MediaAssetResponse(asset.id(), asset.status().name(), asset.contentType(), asset.byteSize(),
                asset.createdAt(), asset.finalisedAt());
    }
}
