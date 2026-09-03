package com.oskott.dogtrainerbackend.training.dto;

import com.oskott.dogtrainerbackend.training.entity.SessionStatus;
import com.oskott.dogtrainerbackend.training.entity.TrainingSession;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TrainingSessionResponse(
        UUID id,
        UUID dogId,
        Instant startedAt,
        Instant completedAt,
        Integer durationMinutes,
        String location,
        String notes,
        SessionStatus status,
        List<SessionExerciseResponse> exercises
) {

    public static TrainingSessionResponse from(TrainingSession session, List<SessionExerciseResponse> exercises) {
        return new TrainingSessionResponse(
                session.getId(),
                session.getDogId(),
                session.getStartedAt(),
                session.getCompletedAt(),
                session.getDurationMinutes(),
                session.getLocation(),
                session.getNotes(),
                session.getStatus(),
                exercises
        );
    }
}
