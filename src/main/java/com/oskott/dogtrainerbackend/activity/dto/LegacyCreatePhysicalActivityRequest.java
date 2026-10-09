package com.oskott.dogtrainerbackend.activity.dto;

import com.oskott.dogtrainerbackend.activity.entity.ActivityType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Body of the deprecated {@code POST /dogs/{dogId}/physical-activities}, still used by app
 * versions released before multi-dog activities. The dog comes from the path instead.
 */
@Deprecated
public record LegacyCreatePhysicalActivityRequest(
        @NotNull ActivityType activityType,
        @Size(max = 255) String title
) {

    public CreatePhysicalActivityRequest toRequest(UUID dogId) {
        return new CreatePhysicalActivityRequest(List.of(dogId), activityType, title);
    }
}
