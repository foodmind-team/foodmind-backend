package com.foodmind.foodmindbackend.media.api.response;

import com.foodmind.foodmindbackend.media.application.CreateMediaUploadUseCase;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * @description: Sensitive short-lived instruction for a single object PUT.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:35 pm
 */

public record MediaUploadInstructionResponse(UUID mediaAssetId, String status, String uploadUrl,
        Map<String, String> requiredHeaders, OffsetDateTime expiresAt) {
    public static MediaUploadInstructionResponse from(CreateMediaUploadUseCase.Result result) {
        return new MediaUploadInstructionResponse(result.asset().id(), result.asset().status().name(),
                result.instruction().uploadUrl(), result.instruction().requiredHeaders(), result.instruction().expiresAt());
    }
}
