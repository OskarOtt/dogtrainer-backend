package com.oskott.dogtrainerbackend.post.dto;

import jakarta.validation.constraints.Size;

public record CreatePostFromActivityRequest(
        @Size(max = 2048) String content
) {
}
