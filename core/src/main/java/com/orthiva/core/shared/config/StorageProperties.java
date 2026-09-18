package com.orthiva.core.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orthiva.storage")
public record StorageProperties(
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        int presignMinutes) {
}
