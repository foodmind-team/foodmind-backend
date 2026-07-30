package com.foodmind.foodmindbackend.media.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.media.application.port.MediaAssetRepository;
import com.foodmind.foodmindbackend.media.application.port.ObjectStoragePort;
import com.foodmind.foodmindbackend.media.domain.model.MediaAsset;
import com.foodmind.foodmindbackend.media.domain.model.MediaAssetStatus;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * @description: Soft-deletes owned assets and leaves failed physical deletion retry-safe.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:50 pm
 */

@Service
@ConditionalOnProperty(prefix = "foodmind.media.storage", name = "enabled", havingValue = "true")
public class DeleteMediaAssetUseCase {

    private final MediaAssetRepository repository;
    private final ObjectStoragePort objectStorage;
    private final Clock clock;

    public DeleteMediaAssetUseCase(MediaAssetRepository repository, ObjectStoragePort objectStorage, Clock clock) {
        this.repository = repository;
        this.objectStorage = objectStorage;
        this.clock = clock;
    }

    public void delete(UUID ownerUserId, UUID assetId) {
        MediaAsset asset = repository.findOwned(ownerUserId, assetId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (asset.status() == MediaAssetStatus.DELETED) {
            return;
        }
        MediaAsset deleted = repository.softDelete(ownerUserId, assetId, OffsetDateTime.now(clock))
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        try {
            objectStorage.deleteObject(deleted.objectKey());
        } catch (ObjectStoragePort.ObjectStorageUnavailableException ignored) {
            // State stays DELETED and the scheduled cleanup will retry.
        }
    }
}
