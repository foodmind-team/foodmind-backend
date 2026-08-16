package com.foodmind.foodmindbackend.media.infrastructure.storage;

import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @description: Bounded object-storage configuration for media uploads.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:30 pm
 */

@Validated
@ConfigurationProperties(prefix = "foodmind.media.storage")
public class S3StorageProperties {

    private boolean enabled;
    private String bucket = "";
    private String region = "us-east-1";
    private String endpoint = "";
    private String presignEndpoint = "";
    private String accessKey = "";
    private String secretKey = "";
    private String keyPrefix = "media";
    private Duration uploadTtl = Duration.ofMinutes(5);
    private Duration readTtl = Duration.ofMinutes(5);
    private long maxByteSize = 5 * 1024 * 1024;
    private Duration cleanupDelay = Duration.ofMinutes(15);
    private Set<String> allowedContentTypes = new LinkedHashSet<>(Set.of("image/jpeg", "image/png", "image/webp"));

    @AssertTrue(message = "Enabled media storage requires a bucket, valid limits, and complete optional static credentials.")
    public boolean isConfigurationValid() {
        boolean credentialsComplete = (accessKey == null || accessKey.isBlank()) == (secretKey == null || secretKey.isBlank());
        return !enabled || (notBlank(bucket) && notBlank(region) && notBlank(keyPrefix)
                && uploadTtl != null && !uploadTtl.isNegative() && !uploadTtl.isZero()
                && readTtl != null && !readTtl.isNegative() && !readTtl.isZero()
                && maxByteSize > 0 && cleanupDelay != null && !cleanupDelay.isNegative() && !cleanupDelay.isZero()
                && allowedContentTypes != null && !allowedContentTypes.isEmpty() && credentialsComplete);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getPresignEndpoint() { return presignEndpoint; }
    public void setPresignEndpoint(String presignEndpoint) { this.presignEndpoint = presignEndpoint; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
    public Duration getUploadTtl() { return uploadTtl; }
    public void setUploadTtl(Duration uploadTtl) { this.uploadTtl = uploadTtl; }
    public Duration getReadTtl() { return readTtl; }
    public void setReadTtl(Duration readTtl) { this.readTtl = readTtl; }
    public long getMaxByteSize() { return maxByteSize; }
    public void setMaxByteSize(long maxByteSize) { this.maxByteSize = maxByteSize; }
    public Duration getCleanupDelay() { return cleanupDelay; }
    public void setCleanupDelay(Duration cleanupDelay) { this.cleanupDelay = cleanupDelay; }
    public Set<String> getAllowedContentTypes() { return Set.copyOf(allowedContentTypes); }
    public void setAllowedContentTypes(Set<String> allowedContentTypes) { this.allowedContentTypes = allowedContentTypes; }
}
