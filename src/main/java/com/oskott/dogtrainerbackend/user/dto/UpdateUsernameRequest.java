package com.oskott.dogtrainerbackend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUsernameRequest(
        @NotBlank
        @Size(max = 30, message = "must be at most 30 characters")
        @Pattern(
                regexp = "^[\\p{L}\\p{N}][\\p{L}\\p{N} .,-]*$",
                message = "can only contain letters, numbers, spaces, and . , -"
        )
        String name
) {
}
