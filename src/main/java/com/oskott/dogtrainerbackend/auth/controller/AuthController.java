package com.oskott.dogtrainerbackend.auth.controller;

import com.oskott.dogtrainerbackend.auth.dto.AuthResponse;
import com.oskott.dogtrainerbackend.auth.dto.AuthMethod;
import com.oskott.dogtrainerbackend.auth.dto.LoginRequest;
import com.oskott.dogtrainerbackend.auth.dto.RefreshRequest;
import com.oskott.dogtrainerbackend.auth.dto.RegisterRequest;
import com.oskott.dogtrainerbackend.auth.dto.SocialAuthRequest;
import com.oskott.dogtrainerbackend.auth.repository.ExternalIdentityRepository;
import com.oskott.dogtrainerbackend.auth.service.AuthService;
import com.oskott.dogtrainerbackend.common.exception.ResourceNotFoundException;
import com.oskott.dogtrainerbackend.common.security.CurrentUserProvider;
import com.oskott.dogtrainerbackend.user.dto.UserResponse;
import com.oskott.dogtrainerbackend.user.entity.User;
import com.oskott.dogtrainerbackend.user.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final ExternalIdentityRepository externalIdentityRepository;

    public AuthController(
            AuthService authService,
            CurrentUserProvider currentUserProvider,
            UserRepository userRepository,
            ExternalIdentityRepository externalIdentityRepository
    ) {
        this.authService = authService;
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
        this.externalIdentityRepository = externalIdentityRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/social")
    public AuthResponse socialLogin(@Valid @RequestBody SocialAuthRequest request) {
        return authService.socialLogin(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserResponse me() {
        User user = userRepository.findById(currentUserProvider.getCurrentUserId())
                .orElseThrow(() -> ResourceNotFoundException.forEntity("User", currentUserProvider.getCurrentUserId()));
        return UserResponse.from(user, externalIdentityRepository.findAllByUserId(user.getId()));
    }

    @PostMapping("/social/link")
    public List<AuthMethod> linkSocialIdentity(@Valid @RequestBody SocialAuthRequest request) {
        return authService.linkSocialIdentity(currentUserProvider.getCurrentUserId(), request);
    }
}
