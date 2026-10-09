package com.oskott.dogtrainerbackend.post.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostResponse(
        UUID id,
        UUID authorId,
        String authorName,
        String authorAvatarUrl,
        List<UUID> dogIds,
        List<String> dogNames,
        UUID trainingSessionId,
        UUID physicalActivityId,
        String content,
        String imageUrl,
        Instant createdAt,
        long likeCount,
        long commentCount,
        boolean likedByMe
) {

    /** @deprecated first dog of {@link #dogIds()}; kept for app versions released before multi-dog posts. */
    @Deprecated
    @JsonProperty("dogId")
    public UUID legacyDogId() {
        return dogIds.isEmpty() ? null : dogIds.getFirst();
    }

    /** @deprecated first name of {@link #dogNames()}; kept for app versions released before multi-dog posts. */
    @Deprecated
    @JsonProperty("dogName")
    public String legacyDogName() {
        return dogNames.isEmpty() ? null : dogNames.getFirst();
    }
}
