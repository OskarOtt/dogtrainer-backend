package com.oskott.dogtrainerbackend.auth.dto;

public record AuthResponse(String accessToken, String refreshToken, long expiresInSeconds) {
}
