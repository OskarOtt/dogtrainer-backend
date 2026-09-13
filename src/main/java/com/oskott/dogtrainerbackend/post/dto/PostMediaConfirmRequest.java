package com.oskott.dogtrainerbackend.post.dto;

import jakarta.validation.constraints.NotBlank;

public record PostMediaConfirmRequest(@NotBlank String objectKey) {
}
