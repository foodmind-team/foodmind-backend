package com.foodmind.foodmindbackend.media.api.request;

import com.foodmind.foodmindbackend.media.application.CreateMediaUploadUseCase;

/**
 * @description: Public bounded media upload declaration.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:35 pm
 */

public record CreateMediaUploadRequest(String contentType, Long byteSize, String checksumSha256) {
    public CreateMediaUploadUseCase.Command toCommand() {
        return new CreateMediaUploadUseCase.Command(contentType, byteSize, checksumSha256);
    }
}
