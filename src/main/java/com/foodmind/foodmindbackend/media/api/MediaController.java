package com.foodmind.foodmindbackend.media.api;

import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import com.foodmind.foodmindbackend.media.api.request.CreateMediaUploadRequest;
import com.foodmind.foodmindbackend.media.api.response.MediaAssetResponse;
import com.foodmind.foodmindbackend.media.api.response.MediaUploadInstructionResponse;
import com.foodmind.foodmindbackend.media.application.CreateMediaUploadUseCase;
import com.foodmind.foodmindbackend.media.application.DeleteMediaAssetUseCase;
import com.foodmind.foodmindbackend.media.application.FinaliseMediaUploadUseCase;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description: Owner-only endpoints for bounded media upload lifecycle operations.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:35 pm
 */

@RestController
@RequestMapping("/api/v1/media")
@ConditionalOnProperty(prefix = "foodmind.media.storage", name = "enabled", havingValue = "true")
public class MediaController {

    private final CreateMediaUploadUseCase createMediaUpload;
    private final FinaliseMediaUploadUseCase finaliseMediaUpload;
    private final DeleteMediaAssetUseCase deleteMediaAsset;

    public MediaController(CreateMediaUploadUseCase createMediaUpload, FinaliseMediaUploadUseCase finaliseMediaUpload,
            DeleteMediaAssetUseCase deleteMediaAsset) {
        this.createMediaUpload = createMediaUpload;
        this.finaliseMediaUpload = finaliseMediaUpload;
        this.deleteMediaAsset = deleteMediaAsset;
    }

    @PostMapping("/uploads")
    ResponseEntity<MediaUploadInstructionResponse> createUpload(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @Valid @RequestBody CreateMediaUploadRequest request) {
        MediaUploadInstructionResponse response = MediaUploadInstructionResponse.from(createMediaUpload.create(principal.id(), request.toCommand()));
        return ResponseEntity.created(URI.create("/api/v1/media/" + response.mediaAssetId())).body(response);
    }

    @PostMapping("/{mediaAssetId}/finalise")
    MediaAssetResponse finalise(@AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable java.util.UUID mediaAssetId) {
        return MediaAssetResponse.from(finaliseMediaUpload.finalise(principal.id(), mediaAssetId));
    }

    @DeleteMapping("/{mediaAssetId}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable java.util.UUID mediaAssetId) {
        deleteMediaAsset.delete(principal.id(), mediaAssetId);
        return ResponseEntity.noContent().build();
    }
}
