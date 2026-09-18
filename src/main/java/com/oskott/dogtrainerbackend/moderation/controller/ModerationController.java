package com.oskott.dogtrainerbackend.moderation.controller;

import com.oskott.dogtrainerbackend.moderation.dto.CreateReportRequest;
import com.oskott.dogtrainerbackend.moderation.service.ModerationService;
import com.oskott.dogtrainerbackend.user.dto.PublicUserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ModerationController {

    private final ModerationService moderationService;

    public ModerationController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @PostMapping("/reports")
    public ResponseEntity<Void> createReport(@Valid @RequestBody CreateReportRequest request) {
        moderationService.createReport(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/users/{id}/block")
    public ResponseEntity<Void> blockUser(@PathVariable UUID id) {
        moderationService.blockUser(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{id}/block")
    public ResponseEntity<Void> unblockUser(@PathVariable UUID id) {
        moderationService.unblockUser(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/me/blocked")
    public List<PublicUserResponse> listBlockedUsers() {
        return moderationService.listBlockedUsers();
    }
}
