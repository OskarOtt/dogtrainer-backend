package com.oskott.dogtrainerbackend.stats.dto;

import com.oskott.dogtrainerbackend.training.dto.TrainingSessionResponse;

import java.util.List;

public record DogProgressResponse(
        List<TrainingSessionResponse> history,
        long totalTrainingMinutes,
        double sessionsPerWeek,
        int currentStreakDays,
        double averageSuccessRate,
        List<ExerciseProgressEntry> exerciseProgress
) {
}
