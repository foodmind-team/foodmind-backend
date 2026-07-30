package com.foodmind.foodmindbackend.media.application;

import com.foodmind.foodmindbackend.common.api.ApiFieldError;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.media.application.port.MediaAssetRepository;
import com.foodmind.foodmindbackend.media.application.port.ObjectStoragePort;
import com.foodmind.foodmindbackend.media.domain.model.MediaAsset;
import com.foodmind.foodmindbackend.media.domain.model.MediaAssetStatus;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * @description: Verifies trusted S3 metadata before atomically finalising an owned asset.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:50 pm
 */

@Service
@ConditionalOnProperty(prefix = "foodmind.media.storage", name = "enabled", havingValue = "true")
public class FinaliseMediaUploadUseCase {

    private final MediaAssetRepository repository;
    private final ObjectStoragePort objectStorage;
    private final Clock clock;

    public FinaliseMediaUploadUseCase(MediaAssetRepository repository, ObjectStoragePort objectStorage, Clock clock) {
        this.repository = repository;
        this.objectStorage = objectStorage;
        this.clock = clock;
    }

    public MediaAsset finalise(UUID ownerUserId, UUID assetId) {
        MediaAsset asset = ownedActiveAsset(ownerUserId, assetId);
        if (asset.status() == MediaAssetStatus.READY) {
            return asset;
        }
        ObjectStoragePort.ObjectMetadata metadata;
        try {
            metadata = objectStorage.headObject(asset.objectKey());
        } catch (ObjectStoragePort.ObjectMissingException exception) {
            throw verificationError("OBJECT_NOT_FOUND", "Uploaded object could not be verified.");
        } catch (ObjectStoragePort.ObjectStorageUnavailableException exception) {
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "Media storage is temporarily unavailable.");
        }
        if (!matches(asset, metadata)) {
            deleteUnexpectedObject(asset);
            throw verificationError("STORAGE_METADATA_MISMATCH", "Uploaded object does not match expected metadata.");
        }
        if (repository.markReady(ownerUserId, assetId, OffsetDateTime.now(clock))) {
            return repository.findOwned(ownerUserId, assetId).orElseThrow(this::notFound);
        }
        MediaAsset current = ownedActiveAsset(ownerUserId, assetId);
        if (current.status() == MediaAssetStatus.READY) {
            return current;
        }
        throw notFound();
    }

    private MediaAsset ownedActiveAsset(UUID ownerUserId, UUID assetId) {
        MediaAsset asset = repository.findOwned(ownerUserId, assetId).orElseThrow(this::notFound);
        if (asset.status() == MediaAssetStatus.DELETED) {
            throw notFound();
        }
        return asset;
    }

    private boolean matches(MediaAsset asset, ObjectStoragePort.ObjectMetadata metadata) {
        return asset.contentType().equals(metadata.contentType()) && asset.byteSize() == metadata.byteSize()
                && asset.checksumSha256().equals(metadata.checksumSha256());
    }

    private void deleteUnexpectedObject(MediaAsset asset) {
        repository.softDelete(asset.ownerUserId(), asset.id(), OffsetDateTime.now(clock));
        try {
            objectStorage.deleteObject(asset.objectKey());
        } catch (ObjectStoragePort.ObjectStorageUnavailableException ignored) {
            // Scheduled cleanup retries this immutable key without changing the DELETED state.
        }
    }

    private ApiException verificationError(String fieldCode, String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, message,
                List.of(new ApiFieldError("upload", fieldCode, message)));
    }

    private ApiException notFound() {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
