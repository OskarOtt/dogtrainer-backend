package com.oskott.dogtrainerbackend.notification.dto;

import com.oskott.dogtrainerbackend.notification.entity.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterDeviceRequest(
        @NotBlank
        @Size(max = 255)
        @Pattern(regexp = "^(Expo|Exponent)PushToken\\[[^\\]\\s]+\\]$", message = "Invalid Expo push token")
        String expoPushToken,
        @NotBlank @Pattern(regexp = "(?i)^(IOS|ANDROID)$", message = "Platform must be IOS or ANDROID") String platform,
        @Size(max = 16) String locale
) {

    public DevicePlatform devicePlatform() {
        return DevicePlatform.valueOf(platform.toUpperCase(java.util.Locale.ROOT));
    }
}