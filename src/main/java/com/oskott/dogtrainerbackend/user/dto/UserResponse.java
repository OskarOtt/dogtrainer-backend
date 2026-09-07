package com.oskott.dogtrainerbackend.user.dto;

import com.oskott.dogtrainerbackend.user.entity.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, String name, String avatarUrl, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getAvatarUrl(),
                user.getCreatedAt()
        );
    }
}
