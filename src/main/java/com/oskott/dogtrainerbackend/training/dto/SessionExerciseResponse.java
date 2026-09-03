package com.oskott.dogtrainerbackend.training.dto;

import com.oskott.dogtrainerbackend.training.entity.Difficulty;
import com.oskott.dogtrainerbackend.training.entity.SessionExercise;

import java.util.UUID;

public record SessionExerciseResponse(
        UUID id,
        UUID exerciseId,
        int repetitions,
        int successfulRepetitions,
        double successRate,
        Difficulty difficulty,
        String notes
) {

    public static SessionExerciseResponse from(SessionExercise sessionExercise) {
        return new SessionExerciseResponse(
                sessionExercise.getId(),
                sessionExercise.getExerciseId(),
                sessionExercise.getRepetitions(),
                sessionExercise.getSuccessfulRepetitions(),
                sessionExercise.successRate(),
                sessionExercise.getDifficulty(),
                sessionExercise.getNotes()
        );
    }
}
