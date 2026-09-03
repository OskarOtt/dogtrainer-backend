package com.oskott.dogtrainerbackend.plan.dto;

import com.oskott.dogtrainerbackend.plan.entity.PlanStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TrainingPlanRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2048) String description,
        LocalDate startDate,
        LocalDate endDate,
        PlanStatus status,
        List<UUID> exerciseIds
) {
}
