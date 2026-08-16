package com.foodmind.foodmindbackend.media.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.media.application.port.MediaAssetRepository;
import com.foodmind.foodmindbackend.media.application.port.ObjectStoragePort;
import com.foodmind.foodmindbackend.media.domain.model.MediaAsset;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates short-lived read instructions without exposing storage keys. */
@Service
@ConditionalOnProperty(prefix = "foodmind.media.storage", name = "enabled", havingValue = "true")
public class CreateMediaAccessUseCase {

    private final MediaAssetRepository repository;
    private final ObjectStoragePort objectStorage;

    public CreateMediaAccessUseCase(MediaAssetRepository repository, ObjectStoragePort objectStorage) {
        this.repository = repository;
        this.objectStorage = objectStorage;
    }

    @Transactional(readOnly = true)
    public Result create(UUID actorUserId, UUID assetId) {
        MediaAsset asset = repository.findAccessibleReady(actorUserId, assetId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        try {
            ObjectStoragePort.ReadInstruction instruction = objectStorage.createReadInstruction(asset.objectKey());
            return new Result(asset.id(), instruction.readUrl(), instruction.expiresAt());
        } catch (ObjectStoragePort.ObjectStorageUnavailableException exception) {
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "Media storage is temporarily unavailable.");
        }
    }

    public record Result(UUID mediaAssetId, String readUrl, OffsetDateTime expiresAt) {
    }
}
