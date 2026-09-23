package com.oskott.dogtrainerbackend.user.dto;

import com.oskott.dogtrainerbackend.auth.dto.AuthMethod;
import com.oskott.dogtrainerbackend.auth.entity.ExternalIdentity;
import com.oskott.dogtrainerbackend.user.entity.User;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String name,
        String avatarUrl,
        Instant createdAt,
        List<AuthMethod> authMethods
) {

    public static UserResponse from(User user, List<ExternalIdentity> identities) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getAvatarUrl(),
                user.getCreatedAt(),
                AuthMethod.resolve(user.hasPassword(), identities)
        );
    }
}
