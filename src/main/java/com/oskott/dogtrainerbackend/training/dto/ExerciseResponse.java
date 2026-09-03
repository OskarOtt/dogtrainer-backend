package com.oskott.dogtrainerbackend.training.dto;

import com.oskott.dogtrainerbackend.training.entity.Difficulty;
import com.oskott.dogtrainerbackend.training.entity.Exercise;

import java.util.UUID;

public record ExerciseResponse(
        UUID id,
        UUID activityId,
        String name,
        String description,
        Difficulty difficulty,
        String instructions
) {

    public static ExerciseResponse from(Exercise exercise) {
        return new ExerciseResponse(
                exercise.getId(),
                exercise.getActivityId(),
                exercise.getName(),
                exercise.getDescription(),
                exercise.getDifficulty(),
                exercise.getInstructions()
        );
    }
}
