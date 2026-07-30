package com.foodmind.foodmindbackend.media.infrastructure.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
}
