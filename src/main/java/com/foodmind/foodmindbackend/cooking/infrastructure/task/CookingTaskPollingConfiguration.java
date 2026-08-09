package com.foodmind.foodmindbackend.cooking.infrastructure.task;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers the async task polling properties (defaults off). */
@Configuration
@EnableConfigurationProperties(CookingTaskPollingProperties.class)
class CookingTaskPollingConfiguration {
}
