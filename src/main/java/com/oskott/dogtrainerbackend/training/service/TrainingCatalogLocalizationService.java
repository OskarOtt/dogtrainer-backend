package com.oskott.dogtrainerbackend.training.service;

import com.oskott.dogtrainerbackend.common.i18n.SupportedLocale;
import com.oskott.dogtrainerbackend.training.dto.ActivityResponse;
import com.oskott.dogtrainerbackend.training.dto.ExerciseResponse;
import com.oskott.dogtrainerbackend.training.dto.TrainingCategoryResponse;
import com.oskott.dogtrainerbackend.training.entity.Activity;
import com.oskott.dogtrainerbackend.training.entity.ActivityTranslation;
import com.oskott.dogtrainerbackend.training.entity.Exercise;
import com.oskott.dogtrainerbackend.training.entity.ExerciseTranslation;
import com.oskott.dogtrainerbackend.training.entity.TrainingCategory;
import com.oskott.dogtrainerbackend.training.entity.TrainingCategoryTranslation;
import com.oskott.dogtrainerbackend.training.repository.ActivityTranslationRepository;
import com.oskott.dogtrainerbackend.training.repository.ExerciseTranslationRepository;
import com.oskott.dogtrainerbackend.training.repository.TrainingCategoryTranslationRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TrainingCatalogLocalizationService {

    private final TrainingCategoryTranslationRepository categoryTranslationRepository;
    private final ActivityTranslationRepository activityTranslationRepository;
    private final ExerciseTranslationRepository exerciseTranslationRepository;

    public TrainingCatalogLocalizationService(
            TrainingCategoryTranslationRepository categoryTranslationRepository,
            ActivityTranslationRepository activityTranslationRepository,
            ExerciseTranslationRepository exerciseTranslationRepository
    ) {
        this.categoryTranslationRepository = categoryTranslationRepository;
        this.activityTranslationRepository = activityTranslationRepository;
        this.exerciseTranslationRepository = exerciseTranslationRepository;
    }

    public List<TrainingCategoryResponse> localizeCategories(List<TrainingCategory> categories) {
        Map<UUID, TrainingCategoryTranslation> translations = categoryTranslations(categories);
        return categories.stream().map(category -> {
            TrainingCategoryTranslation translation = translations.get(category.getId());
            return new TrainingCategoryResponse(
                    category.getId(),
                    translated(translation == null ? null : translation.getName(), category.getName()),
                    translated(translation == null ? null : translation.getDescription(), category.getDescription())
            );
        }).toList();
    }

    public List<ActivityResponse> localizeActivities(List<Activity> activities) {
        Map<UUID, ActivityTranslation> translations = activityTranslations(activities);
        return activities.stream().map(activity -> {
            ActivityTranslation translation = translations.get(activity.getId());
            return new ActivityResponse(
                    activity.getId(),
                    activity.getCategoryId(),
                    translated(translation == null ? null : translation.getName(), activity.getName()),
                    translated(translation == null ? null : translation.getDescription(), activity.getDescription())
            );
        }).toList();
    }

    public List<ExerciseResponse> localizeExercises(List<Exercise> exercises) {
        Map<UUID, ExerciseTranslation> translations = exerciseTranslations(exercises);
        return exercises.stream().map(exercise -> {
            ExerciseTranslation translation = translations.get(exercise.getId());
            return new ExerciseResponse(
                    exercise.getId(),
                    exercise.getActivityId(),
                    translated(translation == null ? null : translation.getName(), exercise.getName()),
                    translated(translation == null ? null : translation.getDescription(), exercise.getDescription()),
                    exercise.getDifficulty(),
                    translated(translation == null ? null : translation.getInstructions(), exercise.getInstructions())
            );
        }).toList();
    }

    public Map<UUID, String> localizedExerciseNames(Collection<Exercise> exercises) {
        Map<UUID, ExerciseTranslation> translations = exerciseTranslations(exercises);
        return exercises.stream().collect(Collectors.toMap(
                Exercise::getId,
                exercise -> {
                    ExerciseTranslation translation = translations.get(exercise.getId());
                    return translated(translation == null ? null : translation.getName(), exercise.getName());
                }
        ));
    }

    private Map<UUID, TrainingCategoryTranslation> categoryTranslations(Collection<TrainingCategory> categories) {
        if (!SupportedLocale.isBokmal() || categories.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = categories.stream().map(TrainingCategory::getId).toList();
        return categoryTranslationRepository.findAllByLocaleAndCategoryIdIn(SupportedLocale.BOKMAL, ids).stream()
                .collect(Collectors.toMap(TrainingCategoryTranslation::getCategoryId, Function.identity()));
    }

    private Map<UUID, ActivityTranslation> activityTranslations(Collection<Activity> activities) {
        if (!SupportedLocale.isBokmal() || activities.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = activities.stream().map(Activity::getId).toList();
        return activityTranslationRepository.findAllByLocaleAndActivityIdIn(SupportedLocale.BOKMAL, ids).stream()
                .collect(Collectors.toMap(ActivityTranslation::getActivityId, Function.identity()));
    }

    private Map<UUID, ExerciseTranslation> exerciseTranslations(Collection<Exercise> exercises) {
        if (!SupportedLocale.isBokmal() || exercises.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = exercises.stream().map(Exercise::getId).toList();
        return exerciseTranslationRepository.findAllByLocaleAndExerciseIdIn(SupportedLocale.BOKMAL, ids).stream()
                .collect(Collectors.toMap(ExerciseTranslation::getExerciseId, Function.identity()));
    }

    private String translated(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
