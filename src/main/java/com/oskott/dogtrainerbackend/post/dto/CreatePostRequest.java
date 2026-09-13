package com.oskott.dogtrainerbackend.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreatePostRequest(
        @NotBlank @Size(max = 2048) String content,
        UUID dogId
) {
}
