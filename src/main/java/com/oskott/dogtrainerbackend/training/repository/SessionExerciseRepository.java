package com.oskott.dogtrainerbackend.training.repository;

import com.oskott.dogtrainerbackend.training.entity.SessionExercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SessionExerciseRepository extends JpaRepository<SessionExercise, UUID> {

    List<SessionExercise> findAllBySessionIdOrderByIdAsc(UUID sessionId);

    void deleteBySessionIdAndId(UUID sessionId, UUID id);
}
