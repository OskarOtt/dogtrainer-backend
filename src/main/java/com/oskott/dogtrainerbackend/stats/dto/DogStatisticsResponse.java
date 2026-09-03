package com.oskott.dogtrainerbackend.stats.dto;

public record DogStatisticsResponse(
        long totalSessions,
        long completedSessions,
        long totalTrainingMinutes,
        long sessionsThisWeek,
        int currentStreakDays,
        double averageSuccessRate
) {
}
