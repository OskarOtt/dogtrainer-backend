package com.oskott.dogtrainerbackend.activity.dto;

import com.oskott.dogtrainerbackend.activity.entity.ActivityType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePhysicalActivityRequest(
        @NotNull ActivityType activityType,
        @Size(max = 255) String title
) {
}
