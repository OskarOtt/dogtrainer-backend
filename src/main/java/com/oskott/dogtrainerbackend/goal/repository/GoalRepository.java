package com.oskott.dogtrainerbackend.goal.repository;

import com.oskott.dogtrainerbackend.goal.entity.Goal;
import com.oskott.dogtrainerbackend.goal.entity.GoalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    List<Goal> findAllByDogIdOrderByCreatedAtDesc(UUID dogId);

    List<Goal> findAllByDogIdAndStatusOrderByCreatedAtDesc(UUID dogId, GoalStatus status);
}
