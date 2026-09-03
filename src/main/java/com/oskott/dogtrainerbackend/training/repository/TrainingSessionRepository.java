package com.oskott.dogtrainerbackend.training.repository;

import com.oskott.dogtrainerbackend.training.entity.TrainingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, UUID> {

    List<TrainingSession> findAllByDogIdOrderByStartedAtDesc(UUID dogId);
}
