package com.oskott.dogtrainerbackend.stats.dto;

import java.util.List;
import java.util.UUID;

public record ExerciseProgressEntry(
        UUID exerciseId,
        String exerciseName,
        List<ExerciseProgressPoint> points
) {
}
