package com.oskott.dogtrainerbackend.user.controller;

import com.oskott.dogtrainerbackend.storage.dto.UploadUrlRequest;
import com.oskott.dogtrainerbackend.storage.dto.UploadUrlResponse;
import com.oskott.dogtrainerbackend.user.dto.AvatarConfirmRequest;
import com.oskott.dogtrainerbackend.user.dto.PublicUserResponse;
import com.oskott.dogtrainerbackend.user.dto.UserResponse;
import com.oskott.dogtrainerbackend.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public PublicUserResponse getUser(@PathVariable UUID id) {
        return userService.getUser(id);
    }

    @GetMapping(params = "email")
    public PublicUserResponse getUserByEmail(@RequestParam String email) {
        return userService.getUserByEmail(email);
    }

    @PostMapping("/me/avatar/upload-url")
    public UploadUrlResponse createAvatarUploadUrl(@Valid @RequestBody UploadUrlRequest request) {
        return userService.createAvatarUploadUrl(request);
    }

    @PutMapping("/me/avatar")
    public UserResponse confirmAvatar(@Valid @RequestBody AvatarConfirmRequest request) {
        return userService.confirmAvatar(request);
    }

    @DeleteMapping("/me/avatar")
    public ResponseEntity<Void> deleteAvatar() {
        userService.deleteAvatar();
        return ResponseEntity.noContent().build();
    }
}
