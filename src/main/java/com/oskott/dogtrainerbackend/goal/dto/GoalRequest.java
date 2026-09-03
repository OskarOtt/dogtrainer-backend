package com.oskott.dogtrainerbackend.goal.dto;

import com.oskott.dogtrainerbackend.goal.entity.GoalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record GoalRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2048) String description,
        LocalDate targetDate,
        GoalStatus status
) {
}
