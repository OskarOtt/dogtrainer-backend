package com.oskott.dogtrainerbackend.plan.dto;

import com.oskott.dogtrainerbackend.plan.entity.PlanStatus;
import com.oskott.dogtrainerbackend.plan.entity.TrainingPlan;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TrainingPlanResponse(
        UUID id,
        UUID dogId,
        String dogName,
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        PlanStatus status,
        List<ExerciseSummary> exercises
) {

    public static TrainingPlanResponse from(TrainingPlan plan, List<ExerciseSummary> exercises) {
        return from(plan, exercises, null);
    }

    public static TrainingPlanResponse from(TrainingPlan plan, List<ExerciseSummary> exercises, String dogName) {
        return new TrainingPlanResponse(
                plan.getId(),
                plan.getDogId(),
                dogName,
                plan.getName(),
                plan.getDescription(),
                plan.getStartDate(),
                plan.getEndDate(),
                plan.getStatus(),
                exercises
        );
    }
}
