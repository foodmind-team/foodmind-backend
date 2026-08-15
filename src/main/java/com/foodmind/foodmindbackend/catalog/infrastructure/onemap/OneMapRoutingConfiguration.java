package com.foodmind.foodmindbackend.catalog.infrastructure.onemap;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OneMapRoutingProperties.class)
class OneMapRoutingConfiguration {
}
