package com.oskott.dogtrainerbackend.notification.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.expo")
@Validated
public record ExpoPushProperties(
        @NotBlank String pushUrl,
        @NotBlank String receiptsUrl,
        /** Optional Expo "enhanced push security" access token; blank when not enabled. */
        String accessToken,
        /** How long to wait after sending before asking Expo for a delivery receipt. */
        Duration receiptDelay,
        Duration receiptPollInterval
) {
}