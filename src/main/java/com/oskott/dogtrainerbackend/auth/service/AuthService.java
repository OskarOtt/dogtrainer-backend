package com.oskott.dogtrainerbackend.auth.service;

import com.oskott.dogtrainerbackend.auth.dto.AuthResponse;
import com.oskott.dogtrainerbackend.auth.dto.LoginRequest;
import com.oskott.dogtrainerbackend.auth.dto.RegisterRequest;
import com.oskott.dogtrainerbackend.auth.entity.RefreshToken;
import com.oskott.dogtrainerbackend.common.exception.AuthenticationFailedException;
import com.oskott.dogtrainerbackend.common.exception.BusinessRuleException;
import com.oskott.dogtrainerbackend.common.security.JwtService;
import com.oskott.dogtrainerbackend.user.entity.User;
import com.oskott.dogtrainerbackend.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessRuleException("An account with this email already exists");
        }
        User user = new User(
                UUID.randomUUID(),
                request.email().toLowerCase(),
                request.name(),
                passwordEncoder.encode(request.password()),
                null,
                Instant.now()
        );
        userRepository.save(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new AuthenticationFailedException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new AuthenticationFailedException("Invalid email or password");
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        RefreshToken rotated = refreshTokenService.rotate(refreshToken);
        User user = rotated.getUser();
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        return new AuthResponse(accessToken, rotated.getToken(), jwtService.getAccessTokenExpirationSeconds());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        RefreshToken refreshToken = refreshTokenService.issue(user);
        return new AuthResponse(accessToken, refreshToken.getToken(), jwtService.getAccessTokenExpirationSeconds());
    }
}
