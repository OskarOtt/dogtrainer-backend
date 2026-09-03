package com.oskott.dogtrainerbackend.training.controller;

import com.oskott.dogtrainerbackend.training.dto.AddSessionExerciseRequest;
import com.oskott.dogtrainerbackend.training.dto.CreateTrainingSessionRequest;
import com.oskott.dogtrainerbackend.training.dto.TrainingSessionResponse;
import com.oskott.dogtrainerbackend.training.dto.UpdateSessionExerciseRequest;
import com.oskott.dogtrainerbackend.training.dto.UpdateTrainingSessionRequest;
import com.oskott.dogtrainerbackend.training.service.TrainingSessionService;
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
public class TrainingSessionController {

    private final TrainingSessionService trainingSessionService;

    public TrainingSessionController(TrainingSessionService trainingSessionService) {
        this.trainingSessionService = trainingSessionService;
    }

    @GetMapping("/dogs/{dogId}/training-sessions")
    public List<TrainingSessionResponse> listSessions(@PathVariable UUID dogId) {
        return trainingSessionService.listSessionsForDog(dogId);
    }

    @PostMapping("/dogs/{dogId}/training-sessions")
    public ResponseEntity<TrainingSessionResponse> createSession(
            @PathVariable UUID dogId,
            @Valid @RequestBody CreateTrainingSessionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(trainingSessionService.createSession(dogId, request));
    }

    @GetMapping("/training-sessions/{id}")
    public TrainingSessionResponse getSession(@PathVariable UUID id) {
        return trainingSessionService.getSession(id);
    }

    @PutMapping("/training-sessions/{id}")
    public TrainingSessionResponse updateSession(@PathVariable UUID id, @Valid @RequestBody UpdateTrainingSessionRequest request) {
        return trainingSessionService.updateSession(id, request);
    }

    @PostMapping("/training-sessions/{id}/complete")
    public TrainingSessionResponse completeSession(@PathVariable UUID id) {
        return trainingSessionService.completeSession(id);
    }

    @PostMapping("/training-sessions/{id}/cancel")
    public TrainingSessionResponse cancelSession(@PathVariable UUID id) {
        return trainingSessionService.cancelSession(id);
    }

    @PostMapping("/training-sessions/{id}/exercises")
    public ResponseEntity<TrainingSessionResponse> addExercise(
            @PathVariable UUID id,
            @Valid @RequestBody AddSessionExerciseRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(trainingSessionService.addSessionExercise(id, request));
    }

    @PutMapping("/training-sessions/{id}/exercises/{exerciseId}")
    public TrainingSessionResponse updateExercise(
            @PathVariable UUID id,
            @PathVariable UUID exerciseId,
            @Valid @RequestBody UpdateSessionExerciseRequest request
    ) {
        return trainingSessionService.updateSessionExercise(id, exerciseId, request);
    }

    @DeleteMapping("/training-sessions/{id}/exercises/{exerciseId}")
    public TrainingSessionResponse removeExercise(@PathVariable UUID id, @PathVariable UUID exerciseId) {
        return trainingSessionService.removeSessionExercise(id, exerciseId);
    }
}
