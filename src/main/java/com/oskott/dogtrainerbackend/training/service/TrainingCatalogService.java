package com.oskott.dogtrainerbackend.training.service;

import com.oskott.dogtrainerbackend.common.exception.ResourceNotFoundException;
import com.oskott.dogtrainerbackend.training.dto.ActivityResponse;
import com.oskott.dogtrainerbackend.training.dto.ExerciseResponse;
import com.oskott.dogtrainerbackend.training.dto.TrainingCategoryResponse;
import com.oskott.dogtrainerbackend.training.repository.ActivityRepository;
import com.oskott.dogtrainerbackend.training.repository.ExerciseRepository;
import com.oskott.dogtrainerbackend.training.repository.TrainingCategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TrainingCatalogService {

    private final TrainingCategoryRepository trainingCategoryRepository;
    private final ActivityRepository activityRepository;
    private final ExerciseRepository exerciseRepository;

    public TrainingCatalogService(
            TrainingCategoryRepository trainingCategoryRepository,
            ActivityRepository activityRepository,
            ExerciseRepository exerciseRepository
    ) {
        this.trainingCategoryRepository = trainingCategoryRepository;
        this.activityRepository = activityRepository;
        this.exerciseRepository = exerciseRepository;
    }

    public List<TrainingCategoryResponse> listCategories() {
        return trainingCategoryRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(TrainingCategoryResponse::from)
                .toList();
    }

    public List<ActivityResponse> listActivitiesForCategory(UUID categoryId) {
        if (!trainingCategoryRepository.existsById(categoryId)) {
            throw ResourceNotFoundException.forEntity("TrainingCategory", categoryId);
        }
        return activityRepository.findAllByCategoryIdOrderByDisplayOrderAsc(categoryId).stream()
                .map(ActivityResponse::from)
                .toList();
    }

    public List<ExerciseResponse> listExercisesForActivity(UUID activityId) {
        if (!activityRepository.existsById(activityId)) {
            throw ResourceNotFoundException.forEntity("Activity", activityId);
        }
        return exerciseRepository.findAllByActivityIdOrderByDisplayOrderAsc(activityId).stream()
                .map(ExerciseResponse::from)
                .toList();
    }

    public ExerciseResponse getExercise(UUID exerciseId) {
        return exerciseRepository.findById(exerciseId)
                .map(ExerciseResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("Exercise", exerciseId));
    }
}
