package com.oskott.dogtrainerbackend.plan.repository;

import com.oskott.dogtrainerbackend.plan.entity.TrainingPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TrainingPlanRepository extends JpaRepository<TrainingPlan, UUID> {

    List<TrainingPlan> findAllByDogIdOrderByStartDateDesc(UUID dogId);

    List<TrainingPlan> findAllByDogIdInOrderByStartDateDesc(List<UUID> dogIds);
}
