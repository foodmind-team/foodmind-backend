package com.foodmind.foodmindbackend.media.application;

import com.foodmind.foodmindbackend.media.application.port.MediaAssetRepository;
import com.foodmind.foodmindbackend.media.application.port.ObjectStoragePort;
import com.foodmind.foodmindbackend.media.domain.model.MediaAsset;
import com.foodmind.foodmindbackend.media.infrastructure.storage.S3StorageProperties;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @description: Cleans expired PENDING assets and retries immutable object deletion.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:50 pm
 */

@Component
@ConditionalOnProperty(prefix = "foodmind.media.storage", name = "enabled", havingValue = "true")
public class MediaAssetCleanup {

    private static final int BATCH_SIZE = 100;
    private final MediaAssetRepository repository;
    private final ObjectStoragePort objectStorage;
    private final S3StorageProperties properties;
    private final Clock clock;

    public MediaAssetCleanup(MediaAssetRepository repository, ObjectStoragePort objectStorage,
            S3StorageProperties properties, Clock clock) {
        this.repository = repository;
        this.objectStorage = objectStorage;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${foodmind.media.storage.cleanup-delay:15m}")
    public void cleanStaleAssets() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        repository.findPendingCreatedBefore(now.minus(properties.getUploadTtl()), BATCH_SIZE).forEach(asset -> {
            repository.softDelete(asset.ownerUserId(), asset.id(), now).ifPresent(this::deleteQuietly);
        });
        repository.findDeletedBefore(now, BATCH_SIZE).forEach(this::deleteQuietly);
    }

    private void deleteQuietly(MediaAsset asset) {
        try {
            objectStorage.deleteObject(asset.objectKey());
        } catch (ObjectStoragePort.ObjectStorageUnavailableException ignored) {
            // The next fixed-delay run retries exactly this immutable key.
        }
    }
}
