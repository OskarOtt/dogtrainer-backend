package com.oskott.dogtrainerbackend.activity.controller;

import com.oskott.dogtrainerbackend.activity.dto.CreateManualPhysicalActivityRequest;
import com.oskott.dogtrainerbackend.activity.dto.CreatePhysicalActivityRequest;
import com.oskott.dogtrainerbackend.activity.dto.PhysicalActivityResponse;
import com.oskott.dogtrainerbackend.activity.dto.UpdatePhysicalActivityRequest;
import com.oskott.dogtrainerbackend.activity.service.PhysicalActivityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
public class PhysicalActivityController {

    private final PhysicalActivityService physicalActivityService;

    public PhysicalActivityController(PhysicalActivityService physicalActivityService) {
        this.physicalActivityService = physicalActivityService;
    }

    @GetMapping("/dogs/{dogId}/physical-activities")
    public List<PhysicalActivityResponse> listActivities(@PathVariable UUID dogId) {
        return physicalActivityService.listActivitiesForDog(dogId);
    }

    @PostMapping("/physical-activities")
    public ResponseEntity<PhysicalActivityResponse> createActivity(@Valid @RequestBody CreatePhysicalActivityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(physicalActivityService.createActivity(request));
    }

    @PostMapping("/physical-activities/manual")
    public ResponseEntity<PhysicalActivityResponse> createManualActivity(@Valid @RequestBody CreateManualPhysicalActivityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(physicalActivityService.createManualActivity(request));
    }

    @GetMapping("/physical-activities/{id}")
    public PhysicalActivityResponse getActivity(@PathVariable UUID id) {
        return physicalActivityService.getActivity(id);
    }

    @PutMapping("/physical-activities/{id}")
    public PhysicalActivityResponse updateActivity(@PathVariable UUID id, @Valid @RequestBody UpdatePhysicalActivityRequest request) {
        return physicalActivityService.updateActivity(id, request);
    }

    @PostMapping("/physical-activities/{id}/pause")
    public PhysicalActivityResponse pauseActivity(@PathVariable UUID id) {
        return physicalActivityService.pauseActivity(id);
    }

    @PostMapping("/physical-activities/{id}/resume")
    public PhysicalActivityResponse resumeActivity(@PathVariable UUID id) {
        return physicalActivityService.resumeActivity(id);
    }

    @PostMapping("/physical-activities/{id}/complete")
    public PhysicalActivityResponse completeActivity(@PathVariable UUID id) {
        return physicalActivityService.completeActivity(id);
    }

    @PostMapping("/physical-activities/{id}/cancel")
    public PhysicalActivityResponse cancelActivity(@PathVariable UUID id) {
        return physicalActivityService.cancelActivity(id);
    }
}
