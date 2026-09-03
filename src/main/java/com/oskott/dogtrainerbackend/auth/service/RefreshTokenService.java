package com.oskott.dogtrainerbackend.auth.service;

import com.oskott.dogtrainerbackend.auth.entity.RefreshToken;
import com.oskott.dogtrainerbackend.auth.repository.RefreshTokenRepository;
import com.oskott.dogtrainerbackend.common.exception.AuthenticationFailedException;
import com.oskott.dogtrainerbackend.user.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Manages opaque, database-backed refresh tokens (rotated on every use).
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshTokenExpirationMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public RefreshToken issue(User user) {
        String tokenValue = generateOpaqueToken();
        Instant now = Instant.now();
        RefreshToken refreshToken = new RefreshToken(
                UUID.randomUUID(), user, tokenValue, now.plusMillis(refreshTokenExpirationMs), now);
        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Validates the given refresh token, revokes it and issues a fresh one for the same user (rotation).
     */
    public RefreshToken rotate(String tokenValue) {
        RefreshToken existing = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new AuthenticationFailedException("Invalid refresh token"));
        if (!existing.isValid()) {
            throw new AuthenticationFailedException("Refresh token is expired or revoked");
        }
        existing.revoke();
        refreshTokenRepository.save(existing);
        return issue(existing.getUser());
    }

    public void revoke(String tokenValue) {
        refreshTokenRepository.findByToken(tokenValue).ifPresent(token -> {
            token.revoke();
            refreshTokenRepository.save(token);
        });
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
