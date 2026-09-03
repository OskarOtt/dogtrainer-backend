package com.oskott.dogtrainerbackend.training.dto;

import com.oskott.dogtrainerbackend.training.entity.TrainingCategory;

import java.util.UUID;

public record TrainingCategoryResponse(UUID id, String name, String description) {

    public static TrainingCategoryResponse from(TrainingCategory category) {
        return new TrainingCategoryResponse(category.getId(), category.getName(), category.getDescription());
    }
}
