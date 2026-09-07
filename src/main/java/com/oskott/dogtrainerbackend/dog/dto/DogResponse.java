package com.oskott.dogtrainerbackend.dog.dto;

import com.oskott.dogtrainerbackend.dog.entity.Dog;
import com.oskott.dogtrainerbackend.dog.entity.DogMediaType;
import com.oskott.dogtrainerbackend.dog.entity.Sex;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DogResponse(
        UUID id,
        String name,
        String breed,
        LocalDate birthDate,
        Sex sex,
        BigDecimal weight,
        String mediaUrl,
        DogMediaType mediaType,
        Instant createdAt
) {

    public static DogResponse from(Dog dog) {
        return new DogResponse(
                dog.getId(),
                dog.getName(),
                dog.getBreed(),
                dog.getBirthDate(),
                dog.getSex(),
                dog.getWeight(),
                dog.getMediaUrl(),
                dog.getMediaType(),
                dog.getCreatedAt()
        );
    }
}
