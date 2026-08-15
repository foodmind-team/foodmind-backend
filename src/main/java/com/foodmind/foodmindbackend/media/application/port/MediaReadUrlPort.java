package com.foodmind.foodmindbackend.media.application.port;

/**
 * Creates a short-lived client-readable URL for one backend-owned object key.
 */
public interface MediaReadUrlPort {
    String createReadUrl(String objectKey);
}
