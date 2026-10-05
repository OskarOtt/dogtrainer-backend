package com.oskott.dogtrainerbackend.user.dto;

import com.oskott.dogtrainerbackend.user.entity.User;

import java.util.UUID;

/**
 * A single user search result. Deliberately a separate type from {@link PublicUserResponse} (used
 * by the existing by-id/by-email lookups) so adding {@code username} here never changes the shape
 * of those other, already-consumed responses.
 */
public record UserSearchResult(UUID id, String name, String username, String avatarUrl) {

    public static UserSearchResult from(User user) {
        return new UserSearchResult(user.getId(), user.getName(), user.getUsername(), user.getAvatarUrl());
    }
}
