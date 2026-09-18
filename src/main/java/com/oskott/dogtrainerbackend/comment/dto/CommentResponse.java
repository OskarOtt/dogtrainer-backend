package com.oskott.dogtrainerbackend.comment.dto;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        UUID postId,
        UUID authorId,
        String authorName,
        String authorAvatarUrl,
        String content,
        Instant createdAt
) {
}
