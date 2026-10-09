package com.oskott.dogtrainerbackend.activity.dto;

import com.oskott.dogtrainerbackend.activity.entity.ActivityType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreatePhysicalActivityRequest(
        @NotEmpty List<UUID> dogIds,
        @NotNull ActivityType activityType,
        @Size(max = 255) String title
) {
}
