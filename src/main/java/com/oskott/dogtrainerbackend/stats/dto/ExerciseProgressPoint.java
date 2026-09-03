package com.oskott.dogtrainerbackend.stats.dto;

import java.time.Instant;

public record ExerciseProgressPoint(
        Instant sessionStartedAt,
        int repetitions,
        int successfulRepetitions,
        double successRate
) {
}
