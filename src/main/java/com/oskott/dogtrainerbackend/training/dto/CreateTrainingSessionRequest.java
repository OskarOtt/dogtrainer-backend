package com.oskott.dogtrainerbackend.training.dto;

import jakarta.validation.constraints.Size;

public record CreateTrainingSessionRequest(
        @Size(max = 255) String location,
        @Size(max = 2048) String notes
) {
}
