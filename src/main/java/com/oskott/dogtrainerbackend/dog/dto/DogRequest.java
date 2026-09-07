package com.oskott.dogtrainerbackend.dog.dto;

import com.oskott.dogtrainerbackend.dog.entity.Sex;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DogRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String breed,
        @PastOrPresent LocalDate birthDate,
        Sex sex,
        @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("999.99") BigDecimal weight
) {
}
