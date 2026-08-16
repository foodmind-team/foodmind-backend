package com.foodmind.foodmindbackend.media.infrastructure.storage;

import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @description: Registers media storage configuration independently of media enablement.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:40 pm
 */

@Configuration
@EnableConfigurationProperties(S3StorageProperties.class)
class MediaStorageConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "foodmind.media.storage", name = "enabled", havingValue = "true")
    S3Client mediaS3Client(S3StorageProperties properties) {
        var builder = S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentialsProvider(properties))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(!properties.getEndpoint().isBlank()).build());
        if (!properties.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "foodmind.media.storage", name = "enabled", havingValue = "true")
    S3Presigner mediaS3Presigner(S3StorageProperties properties) {
        String presignerEndpoint = properties.getPublicEndpoint().isBlank()
                ? properties.getEndpoint()
                : properties.getPublicEndpoint();
        var builder = S3Presigner.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentialsProvider(properties))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(!presignerEndpoint.isBlank()).build());
        if (!presignerEndpoint.isBlank()) {
            builder.endpointOverride(URI.create(presignerEndpoint));
        }
        return builder.build();
    }

    private AwsCredentialsProvider credentialsProvider(S3StorageProperties properties) {
        if (properties.getAccessKey() == null || properties.getAccessKey().isBlank()) {
            return DefaultCredentialsProvider.create();
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey()));
    }
}
