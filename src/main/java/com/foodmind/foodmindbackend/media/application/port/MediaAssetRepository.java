package com.foodmind.foodmindbackend.media.application.port;

import com.foodmind.foodmindbackend.media.domain.model.MediaAsset;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @description: Persistence port for owner-scoped media asset lifecycle changes.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:35 pm
 */

public interface MediaAssetRepository {
    void savePending(MediaAsset asset);
    Optional<MediaAsset> findOwned(UUID ownerUserId, UUID assetId);
    boolean markReady(UUID ownerUserId, UUID assetId, OffsetDateTime finalisedAt);
    Optional<MediaAsset> softDelete(UUID ownerUserId, UUID assetId, OffsetDateTime deletedAt);
    List<MediaAsset> findPendingCreatedBefore(OffsetDateTime cutoff, int limit);
    List<MediaAsset> findDeletedBefore(OffsetDateTime cutoff, int limit);
}
