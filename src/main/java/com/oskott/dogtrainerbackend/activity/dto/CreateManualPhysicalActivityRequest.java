package com.oskott.dogtrainerbackend.activity.dto;

import com.oskott.dogtrainerbackend.activity.entity.ActivityType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Creates a physical activity that already happened, bypassing the normal "start now, finish
 * later" live-tracking flow. Used by the Train tab's "log a past entry" shortcut when a user
 * forgot to track an activity as it happened. The activity is created directly as COMPLETED.
 */
public record CreateManualPhysicalActivityRequest(
        @NotNull ActivityType activityType,
        @Size(max = 255) String title,
        @Size(max = 2048) String notes,
        @NotNull @PastOrPresent Instant startedAt,
        @NotNull @Min(1) @Max(1440) Integer durationMinutes
) {
}
