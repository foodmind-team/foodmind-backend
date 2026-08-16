package com.foodmind.foodmindbackend.catalog.infrastructure.onemap;

import java.util.Objects;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class AwsSecretsManagerOneMapCredentialsSource implements OneMapCredentialsSource {
    private final String secretArn;
    private final SecretsManagerClient secretsManager;
    private final ObjectMapper objectMapper;

    AwsSecretsManagerOneMapCredentialsSource(OneMapRoutingProperties properties, ObjectMapper objectMapper) {
        this(properties.getCredentialsSecretArn(), SecretsManagerClient.builder()
                .region(Region.of(properties.getCredentialsRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build(), objectMapper);
    }

    AwsSecretsManagerOneMapCredentialsSource(
            String secretArn,
            SecretsManagerClient secretsManager,
            ObjectMapper objectMapper) {
        this.secretArn = Objects.requireNonNull(secretArn);
        this.secretsManager = Objects.requireNonNull(secretsManager);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public OneMapCredentials load() {
        try {
            String secret = secretsManager.getSecretValue(GetSecretValueRequest.builder().secretId(secretArn).build())
                    .secretString();
            JsonNode root = objectMapper.readTree(secret);
            return new OneMapCredentials(root.path("email").asText(""), root.path("password").asText(""));
        } catch (RuntimeException exception) {
            throw new OneMapCredentialsUnavailableException();
        }
    }

    static final class OneMapCredentialsUnavailableException extends RuntimeException {
        OneMapCredentialsUnavailableException() {
            super("OneMap credentials are unavailable.");
        }
    }
}
