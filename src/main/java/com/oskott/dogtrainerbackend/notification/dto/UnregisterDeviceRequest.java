package com.oskott.dogtrainerbackend.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnregisterDeviceRequest(@NotBlank @Size(max = 255) String expoPushToken) {
}