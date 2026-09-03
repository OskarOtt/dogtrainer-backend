package com.oskott.dogtrainerbackend.goal.service;

import com.oskott.dogtrainerbackend.common.exception.ResourceNotFoundException;
import com.oskott.dogtrainerbackend.dog.service.DogService;
import com.oskott.dogtrainerbackend.goal.dto.GoalRequest;
import com.oskott.dogtrainerbackend.goal.dto.GoalResponse;
import com.oskott.dogtrainerbackend.goal.entity.Goal;
import com.oskott.dogtrainerbackend.goal.entity.GoalStatus;
import com.oskott.dogtrainerbackend.goal.repository.GoalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class GoalService {

    private final GoalRepository goalRepository;
    private final DogService dogService;

    public GoalService(GoalRepository goalRepository, DogService dogService) {
        this.goalRepository = goalRepository;
        this.dogService = dogService;
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> listGoalsForDog(UUID dogId) {
        dogService.getOwnedDog(dogId);
        return goalRepository.findAllByDogIdOrderByCreatedAtDesc(dogId).stream()
                .map(GoalResponse::from)
                .toList();
    }

    @Transactional
    public GoalResponse createGoal(UUID dogId, GoalRequest request) {
        dogService.getOwnedDog(dogId);
        Goal goal = new Goal(
                UUID.randomUUID(),
                dogId,
                request.title(),
                request.description(),
                request.targetDate(),
                request.status() != null ? request.status() : GoalStatus.NOT_STARTED,
                Instant.now()
        );
        goalRepository.save(goal);
        return GoalResponse.from(goal);
    }

    @Transactional(readOnly = true)
    public GoalResponse getGoal(UUID goalId) {
        return GoalResponse.from(getOwnedGoal(goalId));
    }

    @Transactional
    public GoalResponse updateGoal(UUID goalId, GoalRequest request) {
        Goal goal = getOwnedGoal(goalId);
        goal.setTitle(request.title());
        goal.setDescription(request.description());
        goal.setTargetDate(request.targetDate());
        if (request.status() != null) {
            goal.setStatus(request.status());
        }
        return GoalResponse.from(goal);
    }

    @Transactional
    public void deleteGoal(UUID goalId) {
        Goal goal = getOwnedGoal(goalId);
        goalRepository.delete(goal);
    }

    /**
     * Fetches a goal and enforces that its dog belongs to the currently authenticated user.
     */
    private Goal getOwnedGoal(UUID goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("Goal", goalId));
        // Throws AccessDeniedForResourceException if the dog isn't owned by the current user.
        dogService.getOwnedDog(goal.getDogId());
        return goal;
    }
}
