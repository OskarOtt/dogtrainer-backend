package com.oskott.dogtrainerbackend.post.dto;

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
}
