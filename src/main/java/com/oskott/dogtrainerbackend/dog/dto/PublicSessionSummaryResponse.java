package com.oskott.dogtrainerbackend.dog.dto;

import com.oskott.dogtrainerbackend.training.entity.SessionStatus;
import com.oskott.dogtrainerbackend.training.entity.TrainingSession;

import java.time.Instant;
import java.util.UUID;

/**
 * A trimmed-down view of a {@link TrainingSession} for other users - unlike
 * {@code TrainingSessionResponse}, this deliberately omits location, notes and exercises, which
 * are private to the dog's owner.
 */
public record PublicSessionSummaryResponse(
        UUID id,
        Instant startedAt,
        Instant completedAt,
        Integer durationMinutes,
        SessionStatus status
) {

    public static PublicSessionSummaryResponse from(TrainingSession session) {
        return new PublicSessionSummaryResponse(
                session.getId(),
                session.getStartedAt(),
                session.getCompletedAt(),
                session.getDurationMinutes(),
                session.getStatus()
        );
    }
}
