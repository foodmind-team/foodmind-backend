package com.foodmind.foodmindbackend.media.infrastructure.storage;

import com.foodmind.foodmindbackend.media.application.port.ObjectStoragePort;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * @description: S3-compatible adapter that never exposes cloud credentials or object keys in errors.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:50 pm
 */

@Component
@ConditionalOnProperty(prefix = "foodmind.media.storage", name = "enabled", havingValue = "true")
public class S3ObjectStorageAdapter implements ObjectStoragePort {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3StorageProperties properties;

    public S3ObjectStorageAdapter(S3Client s3Client, S3Presigner s3Presigner, S3StorageProperties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    @Override
    public UploadInstruction createUploadInstruction(String objectKey, String contentType, long byteSize, String checksumSha256) {
        try {
            String checksumBase64 = Base64.getEncoder().encodeToString(HexFormat.of().parseHex(checksumSha256));
            PutObjectRequest request = PutObjectRequest.builder().bucket(properties.getBucket()).key(objectKey)
                    .contentType(contentType).contentLength(byteSize).checksumSHA256(checksumBase64).build();
            PresignedPutObjectRequest signed = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(properties.getUploadTtl()).putObjectRequest(request).build());
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", contentType);
            headers.put("Content-Length", Long.toString(byteSize));
            headers.put("x-amz-checksum-sha256", checksumBase64);
            return new UploadInstruction(signed.url().toExternalForm(), headers,
                    OffsetDateTime.ofInstant(signed.expiration(), ZoneOffset.UTC));
        } catch (RuntimeException exception) {
            throw new ObjectStorageUnavailableException(exception);
        }
    }

    @Override
    public ObjectMetadata headObject(String objectKey) {
        try {
            var object = s3Client.headObject(HeadObjectRequest.builder().bucket(properties.getBucket()).key(objectKey).build());
            String checksum = object.checksumSHA256() == null ? null
                    : HexFormat.of().formatHex(Base64.getDecoder().decode(object.checksumSHA256()));
            return new ObjectMetadata(object.contentType(), object.contentLength(), checksum);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new ObjectMissingException();
            }
            throw new ObjectStorageUnavailableException(exception);
        } catch (RuntimeException exception) {
            throw new ObjectStorageUnavailableException(exception);
        }
    }

    @Override
    public void deleteObject(String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(properties.getBucket()).key(objectKey).build());
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) {
                throw new ObjectStorageUnavailableException(exception);
            }
        } catch (RuntimeException exception) {
            throw new ObjectStorageUnavailableException(exception);
        }
    }
}
