package com.oskott.dogtrainerbackend.plan.dto;

import com.oskott.dogtrainerbackend.training.entity.Exercise;

import java.util.UUID;

public record ExerciseSummary(
        UUID id,
        UUID activityId,
        String name
) {

    public static ExerciseSummary from(Exercise exercise) {
        return new ExerciseSummary(exercise.getId(), exercise.getActivityId(), exercise.getName());
    }
}
