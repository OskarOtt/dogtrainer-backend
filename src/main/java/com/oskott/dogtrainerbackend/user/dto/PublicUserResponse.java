package com.oskott.dogtrainerbackend.user.dto;

import com.oskott.dogtrainerbackend.user.entity.User;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's profile as seen by other users - unlike {@link UserResponse}, this deliberately omits
 * email, since posts/follows/feed expose users to people other than themselves.
 */
public record PublicUserResponse(UUID id, String name, String avatarUrl, Instant createdAt) {

    public static PublicUserResponse from(User user) {
        return new PublicUserResponse(
                user.getId(),
                user.getName(),
                user.getAvatarUrl(),
                user.getCreatedAt()
        );
    }
}
