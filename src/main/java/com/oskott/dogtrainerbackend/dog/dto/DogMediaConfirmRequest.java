package com.oskott.dogtrainerbackend.dog.dto;

import jakarta.validation.constraints.NotBlank;

public record DogMediaConfirmRequest(@NotBlank String objectKey) {
}
