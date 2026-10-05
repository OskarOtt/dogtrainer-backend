package com.oskott.dogtrainerbackend.training.repository;

import com.oskott.dogtrainerbackend.training.entity.SessionStatus;
import com.oskott.dogtrainerbackend.training.entity.TrainingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, UUID> {

    List<TrainingSession> findAllByDogIdOrderByStartedAtDesc(UUID dogId);

    /**
     * Most recent sessions for a dog's public profile - capped since it's summary-only, and
     * scoped to a status (e.g. completed-only, hiding cancelled sessions).
     */
    List<TrainingSession> findTop10ByDogIdAndStatusOrderByStartedAtDesc(UUID dogId, SessionStatus status);

    long countByDogIdAndStatus(UUID dogId, SessionStatus status);
}
