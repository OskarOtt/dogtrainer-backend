package com.oskott.dogtrainerbackend.training.dto;

import com.oskott.dogtrainerbackend.training.entity.Activity;

import java.util.UUID;

public record ActivityResponse(UUID id, UUID categoryId, String name, String description) {

    public static ActivityResponse from(Activity activity) {
        return new ActivityResponse(activity.getId(), activity.getCategoryId(), activity.getName(), activity.getDescription());
    }
}
