package com.oskott.dogtrainerbackend.follow.controller;

import com.oskott.dogtrainerbackend.follow.service.FollowService;
import com.oskott.dogtrainerbackend.user.dto.PublicUserResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/{userId}")
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @PostMapping("/follow")
    public ResponseEntity<Void> follow(@PathVariable UUID userId) {
        followService.follow(userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/follow")
    public ResponseEntity<Void> unfollow(@PathVariable UUID userId) {
        followService.unfollow(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/followers")
    public List<PublicUserResponse> listFollowers(@PathVariable UUID userId) {
        return followService.listFollowers(userId);
    }

    @GetMapping("/following")
    public List<PublicUserResponse> listFollowing(@PathVariable UUID userId) {
        return followService.listFollowing(userId);
    }
}
