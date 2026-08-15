package com.foodmind.foodmindbackend.media.application;

import com.foodmind.foodmindbackend.media.application.port.MediaAssetRepository;
import com.foodmind.foodmindbackend.media.application.port.MediaReadUrlPort;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Resolves storage URLs only after the caller has authorised the parent record.
 * Disabled or unavailable media storage degrades to an absent image.
 */
@Service
public class MediaReadUrlService {

    private final MediaAssetRepository repository;
    private final ObjectProvider<MediaReadUrlPort> readUrlPort;

    public MediaReadUrlService(MediaAssetRepository repository, ObjectProvider<MediaReadUrlPort> readUrlPort) {
        this.repository = repository;
        this.readUrlPort = readUrlPort;
    }

    public String forAuthorisedAsset(UUID mediaAssetId) {
        if (mediaAssetId == null) {
            return null;
        }
        return repository.findReady(mediaAssetId)
                .map(asset -> forAuthorisedObjectKey(asset.objectKey()))
                .orElse(null);
    }

    public String forAuthorisedObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        MediaReadUrlPort port = readUrlPort.getIfAvailable();
        if (port == null) {
            return null;
        }
        try {
            return port.createReadUrl(objectKey);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
