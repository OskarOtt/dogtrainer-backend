package com.oskott.dogtrainerbackend.training.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Creates a training session that already happened, bypassing the normal "start now, finish
 * later" live-tracking flow. Used by the Train tab's "log a past entry" shortcut when a user
 * forgot to track a session as it happened. The session is created directly as COMPLETED.
 */
public record CreateManualTrainingSessionRequest(
        @NotNull @PastOrPresent Instant startedAt,
        @NotNull @Min(1) @Max(1440) Integer durationMinutes,
        @Size(max = 255) String location,
        @Size(max = 2048) String notes
) {
}
