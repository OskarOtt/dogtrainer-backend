package com.oskott.dogtrainerbackend.dog.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record DogReorderRequest(
        @NotEmpty List<UUID> dogIds
) {
}
