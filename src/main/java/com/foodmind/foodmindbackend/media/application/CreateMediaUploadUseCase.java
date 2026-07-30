package com.foodmind.foodmindbackend.media.application;

import com.foodmind.foodmindbackend.media.application.port.MediaAssetRepository;
import com.foodmind.foodmindbackend.media.application.port.ObjectStoragePort;
import com.foodmind.foodmindbackend.media.domain.model.MediaAsset;
import com.foodmind.foodmindbackend.media.domain.model.MediaAssetStatus;
import com.foodmind.foodmindbackend.media.domain.policy.MediaPolicy;
import com.foodmind.foodmindbackend.media.infrastructure.storage.S3StorageProperties;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * @description: Persists a bounded pending asset before issuing its upload instruction.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:35 pm
 */

@Service
@ConditionalOnProperty(prefix = "foodmind.media.storage", name = "enabled", havingValue = "true")
public class CreateMediaUploadUseCase {

    private final MediaAssetRepository repository;
    private final ObjectStoragePort objectStorage;
    private final MediaPolicy mediaPolicy;
    private final S3StorageProperties properties;
    private final Clock clock;

    public CreateMediaUploadUseCase(MediaAssetRepository repository, ObjectStoragePort objectStorage, MediaPolicy mediaPolicy,
            S3StorageProperties properties, Clock clock) {
        this.repository = repository;
        this.objectStorage = objectStorage;
        this.mediaPolicy = mediaPolicy;
        this.properties = properties;
        this.clock = clock;
    }

    public Result create(UUID ownerUserId, Command command) {
        MediaPolicy.Declaration declaration = mediaPolicy.validate(command.contentType(), command.byteSize(), command.checksumSha256());
        UUID assetId = UUID.randomUUID();
        String objectKey = properties.getKeyPrefix().replaceAll("/+$", "") + "/" + ownerUserId + "/" + assetId + "/original";
        MediaAsset asset = new MediaAsset(assetId, ownerUserId, objectKey, declaration.contentType(), declaration.byteSize(),
                declaration.checksumSha256(), MediaAssetStatus.PENDING, OffsetDateTime.now(clock), null, null);
        repository.savePending(asset);
        ObjectStoragePort.UploadInstruction instruction = objectStorage.createUploadInstruction(
                objectKey, declaration.contentType(), declaration.byteSize(), declaration.checksumSha256());
        return new Result(asset, instruction);
    }

    public record Command(String contentType, Long byteSize, String checksumSha256) { }
    public record Result(MediaAsset asset, ObjectStoragePort.UploadInstruction instruction) { }
}
