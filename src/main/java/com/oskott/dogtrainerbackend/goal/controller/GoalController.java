package com.oskott.dogtrainerbackend.goal.controller;

import com.oskott.dogtrainerbackend.goal.dto.GoalRequest;
import com.oskott.dogtrainerbackend.goal.dto.GoalResponse;
import com.oskott.dogtrainerbackend.goal.service.GoalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping("/dogs/{dogId}/goals")
    public List<GoalResponse> listGoals(@PathVariable UUID dogId) {
        return goalService.listGoalsForDog(dogId);
    }

    @PostMapping("/dogs/{dogId}/goals")
    public ResponseEntity<GoalResponse> createGoal(@PathVariable UUID dogId, @Valid @RequestBody GoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(goalService.createGoal(dogId, request));
    }

    @GetMapping("/goals/{id}")
    public GoalResponse getGoal(@PathVariable UUID id) {
        return goalService.getGoal(id);
    }

    @PutMapping("/goals/{id}")
    public GoalResponse updateGoal(@PathVariable UUID id, @Valid @RequestBody GoalRequest request) {
        return goalService.updateGoal(id, request);
    }

    @DeleteMapping("/goals/{id}")
    public ResponseEntity<Void> deleteGoal(@PathVariable UUID id) {
        goalService.deleteGoal(id);
        return ResponseEntity.noContent().build();
    }
}
