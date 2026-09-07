package com.oskott.dogtrainerbackend.storage.dto;

import java.time.Instant;

public record UploadUrlResponse(
        String uploadUrl,
        String objectKey,
        Instant expiresAt
) {
}
