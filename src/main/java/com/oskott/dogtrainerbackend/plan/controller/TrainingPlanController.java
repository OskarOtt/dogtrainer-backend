package com.oskott.dogtrainerbackend.plan.controller;

import com.oskott.dogtrainerbackend.plan.dto.TrainingPlanRequest;
import com.oskott.dogtrainerbackend.plan.dto.TrainingPlanResponse;
import com.oskott.dogtrainerbackend.plan.service.TrainingPlanService;
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
public class TrainingPlanController {

    private final TrainingPlanService trainingPlanService;

    public TrainingPlanController(TrainingPlanService trainingPlanService) {
        this.trainingPlanService = trainingPlanService;
    }

    @GetMapping("/dogs/{dogId}/training-plans")
    public List<TrainingPlanResponse> listPlans(@PathVariable UUID dogId) {
        return trainingPlanService.listPlansForDog(dogId);
    }

    @GetMapping("/training-plans")
    public List<TrainingPlanResponse> listAllPlans() {
        return trainingPlanService.listAllPlansForCurrentUser();
    }

    @PostMapping("/dogs/{dogId}/training-plans")
    public ResponseEntity<TrainingPlanResponse> createPlan(
            @PathVariable UUID dogId,
            @Valid @RequestBody TrainingPlanRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(trainingPlanService.createPlan(dogId, request));
    }

    @GetMapping("/training-plans/{id}")
    public TrainingPlanResponse getPlan(@PathVariable UUID id) {
        return trainingPlanService.getPlan(id);
    }

    @PutMapping("/training-plans/{id}")
    public TrainingPlanResponse updatePlan(@PathVariable UUID id, @Valid @RequestBody TrainingPlanRequest request) {
        return trainingPlanService.updatePlan(id, request);
    }

    @DeleteMapping("/training-plans/{id}")
    public ResponseEntity<Void> deletePlan(@PathVariable UUID id) {
        trainingPlanService.deletePlan(id);
        return ResponseEntity.noContent().build();
    }
}
