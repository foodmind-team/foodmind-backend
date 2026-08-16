package com.foodmind.foodmindbackend.catalog.infrastructure.onemap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import tools.jackson.databind.json.JsonMapper;

class AwsSecretsManagerOneMapCredentialsSourceTest {

    @Test
    void readsOnlyTheExpectedCredentialFields() {
        SecretsManagerClient client = mock(SecretsManagerClient.class);
        when(client.getSecretValue(any(software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder()
                        .secretString("{\"email\":\"owner@example.test\",\"password\":\"safe-password\",\"ignored\":\"value\"}")
                        .build());

        OneMapCredentials credentials = new AwsSecretsManagerOneMapCredentialsSource(
                "arn:aws:secretsmanager:ap-southeast-1:123456789012:secret:foodmind/staging/onemap",
                client,
                JsonMapper.builder().build())
                .load();

        assertThat(credentials.email()).isEqualTo("owner@example.test");
        assertThat(credentials.password()).isEqualTo("safe-password");
    }

    @Test
    void rejectsIncompleteOrMalformedSecretsWithoutEchoingTheirContent() {
        SecretsManagerClient client = mock(SecretsManagerClient.class);
        when(client.getSecretValue(any(software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder()
                        .secretString("{\"email\":\"owner@example.test\",\"password\":\"\"}")
                        .build());

        AwsSecretsManagerOneMapCredentialsSource source = new AwsSecretsManagerOneMapCredentialsSource(
                "arn:aws:secretsmanager:ap-southeast-1:123456789012:secret:foodmind/staging/onemap",
                client,
                JsonMapper.builder().build());

        assertThatThrownBy(source::load)
                .isInstanceOf(AwsSecretsManagerOneMapCredentialsSource.OneMapCredentialsUnavailableException.class)
                .hasMessage("OneMap credentials are unavailable.")
                .hasMessageNotContaining("owner@example.test");
    }
}
