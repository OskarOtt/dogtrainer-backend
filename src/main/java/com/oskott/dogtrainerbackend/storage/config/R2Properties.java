package com.oskott.dogtrainerbackend.storage.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.r2")
public record R2Properties(
        @NotBlank String endpoint,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @NotBlank String bucket,
        @NotBlank @Pattern(regexp = "^https?://.+", message = "must be an absolute URL starting with http:// or https://")
        String publicBaseUrl,
        long presignExpirySeconds
) {
}
