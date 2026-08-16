package com.foodmind.foodmindbackend.media.api.response;

import com.foodmind.foodmindbackend.media.application.CreateMediaAccessUseCase;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Short-lived client-readable media access instruction. */
public record MediaAccessResponse(UUID mediaAssetId, String readUrl, OffsetDateTime expiresAt) {

    public static MediaAccessResponse from(CreateMediaAccessUseCase.Result result) {
        return new MediaAccessResponse(result.mediaAssetId(), result.readUrl(), result.expiresAt());
    }
}
