package com.oskott.dogtrainerbackend.activity.dto;

import com.oskott.dogtrainerbackend.activity.entity.ActivityType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Body of the deprecated {@code POST /dogs/{dogId}/physical-activities/manual}, still used by
 * app versions released before multi-dog activities. The dog comes from the path instead.
 */
@Deprecated
public record LegacyCreateManualPhysicalActivityRequest(
        @NotNull ActivityType activityType,
        @Size(max = 255) String title,
        @Size(max = 2048) String notes,
        @NotNull @PastOrPresent Instant startedAt,
        @NotNull @Min(1) @Max(1440) Integer durationMinutes
) {

    public CreateManualPhysicalActivityRequest toRequest(UUID dogId) {
        return new CreateManualPhysicalActivityRequest(List.of(dogId), activityType, title, notes, startedAt, durationMinutes);
    }
}
