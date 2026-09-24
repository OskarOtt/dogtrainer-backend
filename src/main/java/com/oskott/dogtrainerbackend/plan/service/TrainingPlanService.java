package com.oskott.dogtrainerbackend.plan.service;

import com.oskott.dogtrainerbackend.common.exception.ResourceNotFoundException;
import com.oskott.dogtrainerbackend.common.security.CurrentUserProvider;
import com.oskott.dogtrainerbackend.dog.entity.Dog;
import com.oskott.dogtrainerbackend.dog.repository.DogRepository;
import com.oskott.dogtrainerbackend.dog.service.DogService;
import com.oskott.dogtrainerbackend.plan.dto.ExerciseSummary;
import com.oskott.dogtrainerbackend.plan.dto.TrainingPlanRequest;
import com.oskott.dogtrainerbackend.plan.dto.TrainingPlanResponse;
import com.oskott.dogtrainerbackend.plan.entity.PlanStatus;
import com.oskott.dogtrainerbackend.plan.entity.TrainingPlan;
import com.oskott.dogtrainerbackend.plan.repository.TrainingPlanRepository;
import com.oskott.dogtrainerbackend.training.entity.Exercise;
import com.oskott.dogtrainerbackend.training.repository.ExerciseRepository;
import com.oskott.dogtrainerbackend.training.service.TrainingCatalogLocalizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TrainingPlanService {

    private final TrainingPlanRepository trainingPlanRepository;
    private final DogService dogService;
    private final DogRepository dogRepository;
    private final ExerciseRepository exerciseRepository;
    private final CurrentUserProvider currentUserProvider;
    private final TrainingCatalogLocalizationService catalogLocalizationService;

    public TrainingPlanService(
            TrainingPlanRepository trainingPlanRepository,
            DogService dogService,
            DogRepository dogRepository,
            ExerciseRepository exerciseRepository,
            CurrentUserProvider currentUserProvider,
            TrainingCatalogLocalizationService catalogLocalizationService
    ) {
        this.trainingPlanRepository = trainingPlanRepository;
        this.dogService = dogService;
        this.dogRepository = dogRepository;
        this.exerciseRepository = exerciseRepository;
        this.currentUserProvider = currentUserProvider;
        this.catalogLocalizationService = catalogLocalizationService;
    }

    @Transactional(readOnly = true)
    public List<TrainingPlanResponse> listPlansForDog(UUID dogId) {
        dogService.getOwnedDog(dogId);
        List<TrainingPlan> plans = trainingPlanRepository.findAllByDogIdOrderByStartDateDesc(dogId);
        Map<UUID, ExerciseSummary> exercisesById = resolveExerciseSummaries(plans);
        return plans.stream()
                .map(plan -> TrainingPlanResponse.from(plan, orderedExercises(plan, exercisesById)))
                .toList();
    }

    /**
     * Lists every training plan belonging to any dog owned by the current user, each annotated
     * with its dog's name, so the "Start Training" screen can present one combined list.
     */
    @Transactional(readOnly = true)
    public List<TrainingPlanResponse> listAllPlansForCurrentUser() {
        UUID ownerId = currentUserProvider.getCurrentUserId();
        List<Dog> dogs = dogRepository.findAllByOwnerIdOrderBySortOrderAsc(ownerId);
        if (dogs.isEmpty()) {
            return Collections.emptyList();
        }
        Map<UUID, String> dogNamesById = dogs.stream().collect(Collectors.toMap(Dog::getId, Dog::getName));
        List<UUID> dogIds = dogs.stream().map(Dog::getId).toList();
        List<TrainingPlan> plans = trainingPlanRepository.findAllByDogIdInOrderByStartDateDesc(dogIds);
        Map<UUID, ExerciseSummary> exercisesById = resolveExerciseSummaries(plans);
        return plans.stream()
                .map(plan -> TrainingPlanResponse.from(
                        plan,
                        orderedExercises(plan, exercisesById),
                        dogNamesById.get(plan.getDogId())))
                .toList();
    }

    @Transactional
    public TrainingPlanResponse createPlan(UUID dogId, TrainingPlanRequest request) {
        dogService.getOwnedDog(dogId);
        List<UUID> exerciseIds = validateExerciseIds(request.exerciseIds());
        TrainingPlan plan = new TrainingPlan(
                UUID.randomUUID(),
                dogId,
                request.name(),
                request.description(),
                request.startDate(),
                request.endDate(),
                request.status() != null ? request.status() : PlanStatus.NOT_STARTED,
                exerciseIds
        );
        trainingPlanRepository.save(plan);
        return TrainingPlanResponse.from(plan, resolveExercises(plan));
    }

    @Transactional(readOnly = true)
    public TrainingPlanResponse getPlan(UUID planId) {
        TrainingPlan plan = getOwnedPlan(planId);
        return TrainingPlanResponse.from(plan, resolveExercises(plan));
    }

    @Transactional
    public TrainingPlanResponse updatePlan(UUID planId, TrainingPlanRequest request) {
        TrainingPlan plan = getOwnedPlan(planId);
        plan.setName(request.name());
        plan.setDescription(request.description());
        plan.setStartDate(request.startDate());
        plan.setEndDate(request.endDate());
        if (request.status() != null) {
            plan.setStatus(request.status());
        }
        plan.setExerciseIds(validateExerciseIds(request.exerciseIds()));
        return TrainingPlanResponse.from(plan, resolveExercises(plan));
    }

    @Transactional
    public void deletePlan(UUID planId) {
        TrainingPlan plan = getOwnedPlan(planId);
        trainingPlanRepository.delete(plan);
    }

    /**
     * Fetches a training plan and enforces that its dog belongs to the currently authenticated user.
     */
    private TrainingPlan getOwnedPlan(UUID planId) {
        TrainingPlan plan = trainingPlanRepository.findById(planId)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("TrainingPlan", planId));
        // Throws AccessDeniedForResourceException if the dog isn't owned by the current user.
        dogService.getOwnedDog(plan.getDogId());
        return plan;
    }

    /**
     * Confirms every requested exercise id actually exists in the training catalog before it's
     * attached to a plan, so plans never reference stale/invalid exercises.
     */
    private List<UUID> validateExerciseIds(List<UUID> exerciseIds) {
        if (exerciseIds == null || exerciseIds.isEmpty()) {
            return List.of();
        }
        List<Exercise> found = exerciseRepository.findAllByIdIn(exerciseIds);
        if (found.size() != new HashSet<>(exerciseIds).size()) {
            throw new ResourceNotFoundException("One or more exercises referenced by this plan do not exist");
        }
        return List.copyOf(exerciseIds);
    }

    /**
     * Resolves a plan's stored exercise ids into display-ready summaries, preserving the order
     * the user picked them in.
     */
    private List<ExerciseSummary> resolveExercises(TrainingPlan plan) {
        return orderedExercises(plan, resolveExerciseSummaries(List.of(plan)));
    }

    private Map<UUID, ExerciseSummary> resolveExerciseSummaries(Collection<TrainingPlan> plans) {
        List<UUID> exerciseIds = plans.stream()
                .flatMap(plan -> plan.getExerciseIds().stream())
                .distinct()
                .toList();
        if (exerciseIds.isEmpty()) {
            return Map.of();
        }
        List<Exercise> exercises = exerciseRepository.findAllByIdIn(exerciseIds);
        Map<UUID, String> localizedNames = catalogLocalizationService.localizedExerciseNames(exercises);
        return exercises.stream().collect(Collectors.toMap(
                Exercise::getId,
                exercise -> new ExerciseSummary(
                        exercise.getId(),
                        exercise.getActivityId(),
                        localizedNames.getOrDefault(exercise.getId(), exercise.getName()))
        ));
    }

    private List<ExerciseSummary> orderedExercises(
            TrainingPlan plan,
            Map<UUID, ExerciseSummary> exercisesById
    ) {
        if (plan.getExerciseIds().isEmpty()) {
            return List.of();
        }
        return plan.getExerciseIds().stream()
                .map(exercisesById::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
