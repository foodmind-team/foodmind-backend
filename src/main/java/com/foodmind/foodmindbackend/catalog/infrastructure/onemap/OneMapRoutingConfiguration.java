package com.foodmind.foodmindbackend.catalog.infrastructure.onemap;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(OneMapRoutingProperties.class)
class OneMapRoutingConfiguration {
    @Bean
    OneMapCredentialsSource oneMapCredentialsSource(
            OneMapRoutingProperties properties,
            ObjectMapper objectMapper) {
        if (properties.getCredentialsSecretArn().isBlank()) {
            return () -> {
                throw new AwsSecretsManagerOneMapCredentialsSource.OneMapCredentialsUnavailableException();
            };
        }
        return new AwsSecretsManagerOneMapCredentialsSource(properties, objectMapper);
    }
}
