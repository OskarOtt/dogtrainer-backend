package com.oskott.dogtrainerbackend.training.dto;

import com.oskott.dogtrainerbackend.training.entity.Difficulty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AddSessionExerciseRequest(
        @NotNull UUID exerciseId,
        @Min(0) Integer repetitions,
        @Min(0) Integer successfulRepetitions,
        Difficulty difficulty,
        @Size(max = 2048) String notes
) {
}
