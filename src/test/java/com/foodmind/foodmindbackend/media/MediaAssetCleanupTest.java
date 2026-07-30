package com.foodmind.foodmindbackend.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.foodmind.foodmindbackend.media.application.MediaAssetCleanup;
import com.foodmind.foodmindbackend.media.application.port.MediaAssetRepository;
import com.foodmind.foodmindbackend.media.application.port.ObjectStoragePort;
import com.foodmind.foodmindbackend.media.domain.model.MediaAsset;
import com.foodmind.foodmindbackend.media.domain.model.MediaAssetStatus;
import com.foodmind.foodmindbackend.media.infrastructure.storage.S3StorageProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * @description: Verifies stale media cleanup is bounded and retry-safe without a storage service.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:58 pm
 */

class MediaAssetCleanupTest {

    @Test
    void expiresPendingAssetsBeforeDeletingTheirImmutableObjectKeys() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-30T16:00:00Z");
        MediaAsset pending = new MediaAsset(UUID.randomUUID(), UUID.randomUUID(), "media/owner/asset/original",
                "image/jpeg", 128, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                MediaAssetStatus.PENDING, now.minusMinutes(6), null, null);
        FakeRepository repository = new FakeRepository(pending);
        List<String> deletedKeys = new ArrayList<>();
        ObjectStoragePort storage = new ObjectStoragePort() {
            @Override public UploadInstruction createUploadInstruction(String key, String type, long size, String checksum) { throw new UnsupportedOperationException(); }
            @Override public ObjectMetadata headObject(String key) { throw new UnsupportedOperationException(); }
            @Override public void deleteObject(String key) { deletedKeys.add(key); }
        };
        S3StorageProperties properties = new S3StorageProperties();
        properties.setUploadTtl(java.time.Duration.ofMinutes(5));

        new MediaAssetCleanup(repository, storage, properties, Clock.fixed(Instant.parse("2026-07-30T16:00:00Z"), ZoneOffset.UTC))
                .cleanStaleAssets();

        assertThat(repository.softDeleted).contains(pending.id());
        assertThat(deletedKeys).containsExactly(pending.objectKey());
    }

    private static final class FakeRepository implements MediaAssetRepository {
        private final MediaAsset pending;
        private final List<UUID> softDeleted = new ArrayList<>();

        private FakeRepository(MediaAsset pending) { this.pending = pending; }
        @Override public void savePending(MediaAsset asset) { }
        @Override public Optional<MediaAsset> findOwned(UUID owner, UUID id) { return Optional.empty(); }
        @Override public boolean markReady(UUID owner, UUID id, OffsetDateTime at) { return false; }
        @Override public Optional<MediaAsset> softDelete(UUID owner, UUID id, OffsetDateTime at) {
            softDeleted.add(id);
            return Optional.of(new MediaAsset(pending.id(), pending.ownerUserId(), pending.objectKey(), pending.contentType(),
                    pending.byteSize(), pending.checksumSha256(), MediaAssetStatus.DELETED, pending.createdAt(), null, at));
        }
        @Override public List<MediaAsset> findPendingCreatedBefore(OffsetDateTime cutoff, int limit) { return List.of(pending); }
        @Override public List<MediaAsset> findDeletedBefore(OffsetDateTime cutoff, int limit) { return List.of(); }
    }
}
