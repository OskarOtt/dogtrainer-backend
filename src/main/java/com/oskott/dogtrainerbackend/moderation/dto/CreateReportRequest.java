package com.oskott.dogtrainerbackend.moderation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * At least one of {@code postId}/{@code reportedUserId} must be present - enforced in
 * {@code ModerationService}, since bean validation can't easily express "either/or" here.
 */
public record CreateReportRequest(
        UUID postId,
        UUID reportedUserId,
        @NotBlank String reason,
        @Size(max = 1024) String details
) {
}
