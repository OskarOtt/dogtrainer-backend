package com.oskott.dogtrainerbackend.post.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code dogId} is deprecated and only accepted for app versions released before multi-dog posts;
 * new clients send {@code dogIds}.
 */
public record CreatePostRequest(
        @NotBlank @Size(max = 2048) String content,
        List<UUID> dogIds,
        @Deprecated UUID dogId
) {

    /** Merges the legacy single {@code dogId} into {@code dogIds}; never returns null. */
    @JsonIgnore
    public List<UUID> resolvedDogIds() {
        List<UUID> all = new ArrayList<>(dogIds != null ? dogIds : List.of());
        if (dogId != null && !all.contains(dogId)) {
            all.addFirst(dogId);
        }
        return all;
    }
}
