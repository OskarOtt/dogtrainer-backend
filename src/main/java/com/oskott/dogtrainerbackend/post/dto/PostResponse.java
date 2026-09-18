package com.oskott.dogtrainerbackend.post.dto;

import java.time.Instant;
import java.util.UUID;

public record PostResponse(
        UUID id,
        UUID authorId,
        String authorName,
        String authorAvatarUrl,
        UUID dogId,
        String dogName,
        UUID trainingSessionId,
        String content,
        String imageUrl,
        Instant createdAt,
        long likeCount,
        long commentCount,
        boolean likedByMe
) {
}
