package com.oskott.dogtrainerbackend.user.controller;

import com.oskott.dogtrainerbackend.storage.dto.UploadUrlRequest;
import com.oskott.dogtrainerbackend.storage.dto.UploadUrlResponse;
import com.oskott.dogtrainerbackend.user.dto.AvatarConfirmRequest;
import com.oskott.dogtrainerbackend.user.dto.UserResponse;
import com.oskott.dogtrainerbackend.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/avatar/upload-url")
    public UploadUrlResponse createAvatarUploadUrl(@Valid @RequestBody UploadUrlRequest request) {
        return userService.createAvatarUploadUrl(request);
    }

    @PutMapping("/avatar")
    public UserResponse confirmAvatar(@Valid @RequestBody AvatarConfirmRequest request) {
        return userService.confirmAvatar(request);
    }

    @DeleteMapping("/avatar")
    public ResponseEntity<Void> deleteAvatar() {
        userService.deleteAvatar();
        return ResponseEntity.noContent().build();
    }
}
