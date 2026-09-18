package com.orthiva.core.shared.config;

import java.net.URI;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 clients pointed at MinIO in development. Swapping to Cloudflare R2 or AWS S3
 * in production is a matter of changing the endpoint/credentials, not the code.
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    S3Client s3Client(StorageProperties props) {
        return S3Client.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(Region.of(props.region()))
                .credentialsProvider(credentials(props))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Bean
    S3Presigner s3Presigner(StorageProperties props) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(Region.of(props.region()))
                .credentialsProvider(credentials(props))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private static StaticCredentialsProvider credentials(StorageProperties props) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
    }
}
