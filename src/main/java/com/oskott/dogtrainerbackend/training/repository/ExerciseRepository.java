package com.oskott.dogtrainerbackend.training.repository;

import com.oskott.dogtrainerbackend.training.entity.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExerciseRepository extends JpaRepository<Exercise, UUID> {

    List<Exercise> findAllByActivityIdOrderByDisplayOrderAsc(UUID activityId);

    List<Exercise> findAllByIdIn(List<UUID> ids);
}
