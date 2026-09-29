package com.oskott.dogtrainerbackend.title.dto;

import com.oskott.dogtrainerbackend.title.entity.DogTitle;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DogTitleResponse(
        UUID id,
        UUID dogId,
        String title,
        LocalDate dateEarned,
        Instant createdAt
) {

    public static DogTitleResponse from(DogTitle dogTitle) {
        return new DogTitleResponse(
                dogTitle.getId(),
                dogTitle.getDogId(),
                dogTitle.getTitle(),
                dogTitle.getDateEarned(),
                dogTitle.getCreatedAt()
        );
    }
}
