package com.oskott.dogtrainerbackend.storage.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Cloudflare R2 is S3-compatible but requires path-style addressing and a fixed "auto" region,
 * so both clients are built explicitly rather than relying on AWS SDK auto-configuration.
 */
@Configuration
@EnableConfigurationProperties(R2Properties.class)
public class R2ClientConfig {

    private static final Region R2_REGION = Region.of("auto");

    @Bean
    public S3Client s3Client(R2Properties properties) {
        return S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(R2_REGION)
                .credentialsProvider(credentialsProvider(properties))
                .forcePathStyle(true)
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(R2Properties properties) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(R2_REGION)
                .credentialsProvider(credentialsProvider(properties))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private AwsCredentialsProvider credentialsProvider(R2Properties properties) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }
}
