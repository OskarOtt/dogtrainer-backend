package com.oskott.dogtrainerbackend.training.repository;

import com.oskott.dogtrainerbackend.training.entity.SessionStatus;
import com.oskott.dogtrainerbackend.training.entity.TrainingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, UUID> {

    List<TrainingSession> findAllByDogIdOrderByStartedAtDesc(UUID dogId);

    /** Most recent sessions for a dog's public profile - capped since it's summary-only. */
    List<TrainingSession> findTop10ByDogIdOrderByStartedAtDesc(UUID dogId);

    long countByDogIdAndStatus(UUID dogId, SessionStatus status);
}
