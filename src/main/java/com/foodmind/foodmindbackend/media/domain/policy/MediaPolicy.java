package com.foodmind.foodmindbackend.media.domain.policy;

import com.foodmind.foodmindbackend.common.api.ApiFieldError;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.media.infrastructure.storage.S3StorageProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * @description: Validates bounded, image-only media upload declarations.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:35 pm
 */

@Component
public class MediaPolicy {

    private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");

    private final S3StorageProperties properties;

    public MediaPolicy(S3StorageProperties properties) {
        this.properties = properties;
    }

    public Declaration validate(String contentType, Long byteSize, String checksumSha256) {
        List<ApiFieldError> errors = new ArrayList<>();
        String normalisedType = contentType == null ? null : contentType.trim().toLowerCase(Locale.ROOT);
        Set<String> allowedTypes = properties.getAllowedContentTypes().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (normalisedType == null || !allowedTypes.contains(normalisedType)) {
            errors.add(new ApiFieldError("contentType", "UNSUPPORTED_MEDIA_TYPE", "Content type is not supported."));
        }
        if (byteSize == null || byteSize <= 0 || byteSize > properties.getMaxByteSize()) {
            errors.add(new ApiFieldError("byteSize", "MEDIA_SIZE_OUT_OF_RANGE", "Byte size must be within the configured limit."));
        }
        String normalisedChecksum = checksumSha256 == null ? null : checksumSha256.trim().toLowerCase(Locale.ROOT);
        if (normalisedChecksum == null || !SHA_256.matcher(normalisedChecksum).matches()) {
            errors.add(new ApiFieldError("checksumSha256", "INVALID_SHA256", "Checksum must be 64 lowercase hexadecimal characters."));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), errors);
        }
        return new Declaration(normalisedType, byteSize, normalisedChecksum);
    }

    public record Declaration(String contentType, long byteSize, String checksumSha256) {
    }
}
