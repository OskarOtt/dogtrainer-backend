package com.oskott.dogtrainerbackend.activity.dto;

import com.oskott.dogtrainerbackend.activity.entity.ActivityStatus;
import com.oskott.dogtrainerbackend.activity.entity.ActivityType;
import com.oskott.dogtrainerbackend.activity.entity.PhysicalActivity;

import java.time.Instant;
import java.util.UUID;

public record PhysicalActivityResponse(
        UUID id,
        UUID dogId,
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

    public static PhysicalActivityResponse from(PhysicalActivity activity) {
        return new PhysicalActivityResponse(
                activity.getId(),
                activity.getDogId(),
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
