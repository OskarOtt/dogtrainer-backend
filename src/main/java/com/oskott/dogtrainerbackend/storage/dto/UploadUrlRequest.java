package com.oskott.dogtrainerbackend.storage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record UploadUrlRequest(
        @NotBlank String contentType,
        @Positive long fileSizeBytes
) {
}
