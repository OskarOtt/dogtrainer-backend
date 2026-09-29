package com.oskott.dogtrainerbackend.title.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DogTitleRequest(
        @NotBlank @Size(max = 255) String title,
        @PastOrPresent LocalDate dateEarned
) {
}
