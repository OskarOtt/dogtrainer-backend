package com.oskott.dogtrainerbackend.training.controller;

import com.oskott.dogtrainerbackend.training.dto.ActivityResponse;
import com.oskott.dogtrainerbackend.training.dto.ExerciseResponse;
import com.oskott.dogtrainerbackend.training.dto.TrainingCategoryResponse;
import com.oskott.dogtrainerbackend.training.service.TrainingCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training")
public class TrainingCatalogController {

    private final TrainingCatalogService trainingCatalogService;

    public TrainingCatalogController(TrainingCatalogService trainingCatalogService) {
        this.trainingCatalogService = trainingCatalogService;
    }

    @GetMapping("/categories")
    public List<TrainingCategoryResponse> listCategories() {
        return trainingCatalogService.listCategories();
    }

    @GetMapping("/categories/{id}/activities")
    public List<ActivityResponse> listActivities(@PathVariable UUID id) {
        return trainingCatalogService.listActivitiesForCategory(id);
    }

    @GetMapping("/activities/{id}/exercises")
    public List<ExerciseResponse> listExercises(@PathVariable UUID id) {
        return trainingCatalogService.listExercisesForActivity(id);
    }

    @GetMapping("/exercises/{id}")
    public ExerciseResponse getExercise(@PathVariable UUID id) {
        return trainingCatalogService.getExercise(id);
    }
}
