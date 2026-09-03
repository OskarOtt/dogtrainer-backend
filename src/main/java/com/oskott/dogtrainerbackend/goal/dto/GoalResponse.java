package com.oskott.dogtrainerbackend.goal.dto;

import com.oskott.dogtrainerbackend.goal.entity.Goal;
import com.oskott.dogtrainerbackend.goal.entity.GoalStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record GoalResponse(
        UUID id,
        UUID dogId,
        String title,
        String description,
        LocalDate targetDate,
        GoalStatus status,
        Instant createdAt
) {

    public static GoalResponse from(Goal goal) {
        return new GoalResponse(
                goal.getId(),
                goal.getDogId(),
                goal.getTitle(),
                goal.getDescription(),
                goal.getTargetDate(),
                goal.getStatus(),
                goal.getCreatedAt()
        );
    }
}
