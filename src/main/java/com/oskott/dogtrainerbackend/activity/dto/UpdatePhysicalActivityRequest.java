package com.oskott.dogtrainerbackend.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePhysicalActivityRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2048) String notes
) {
}
