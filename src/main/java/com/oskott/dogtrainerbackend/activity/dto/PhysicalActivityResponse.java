package com.oskott.dogtrainerbackend.activity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oskott.dogtrainerbackend.activity.entity.ActivityStatus;
import com.oskott.dogtrainerbackend.activity.entity.ActivityType;
import com.oskott.dogtrainerbackend.activity.entity.PhysicalActivity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PhysicalActivityResponse(
        UUID id,
        List<UUID> dogIds,
        ActivityType activityType,
        String title,
        String notes,
        Instant startedAt,
        Instant pausedAt,
        long totalPausedSeconds,
        Instant completedAt,
        Integer durationMinutes,
        ActivityStatus status
) {

    /** @deprecated first dog of {@link #dogIds()}; kept for app versions released before multi-dog activities. */
    @Deprecated
    @JsonProperty("dogId")
    public UUID legacyDogId() {
        return dogIds.isEmpty() ? null : dogIds.getFirst();
    }

    public static PhysicalActivityResponse from(PhysicalActivity activity) {
        return new PhysicalActivityResponse(
                activity.getId(),
                activity.getDogIds(),
                activity.getActivityType(),
                activity.getTitle(),
                activity.getNotes(),
                activity.getStartedAt(),
                activity.getPausedAt(),
                activity.getTotalPausedSeconds(),
                activity.getCompletedAt(),
                activity.getDurationMinutes(),
                activity.getStatus()
        );
    }
}
