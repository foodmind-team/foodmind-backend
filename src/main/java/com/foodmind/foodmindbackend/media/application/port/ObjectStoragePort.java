package com.foodmind.foodmindbackend.media.application.port;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * @description: Provider-neutral, limited object-storage operations for media assets.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:35 pm
 */

public interface ObjectStoragePort {
    UploadInstruction createUploadInstruction(String objectKey, String contentType, long byteSize, String checksumSha256);
    ReadInstruction createReadInstruction(String objectKey);
    ObjectMetadata headObject(String objectKey);
    void deleteObject(String objectKey);

    record UploadInstruction(String uploadUrl, Map<String, String> requiredHeaders, OffsetDateTime expiresAt) {
        public UploadInstruction { requiredHeaders = Map.copyOf(requiredHeaders); }
    }

    record ReadInstruction(String readUrl, OffsetDateTime expiresAt) {
    }

    record ObjectMetadata(String contentType, long byteSize, String checksumSha256) {
    }

    class ObjectMissingException extends RuntimeException {
        public ObjectMissingException() { super(); }
    }

    class ObjectStorageUnavailableException extends RuntimeException {
        public ObjectStorageUnavailableException(Throwable cause) { super(cause); }
    }
}
