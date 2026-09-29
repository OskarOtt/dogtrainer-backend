package com.oskott.dogtrainerbackend.dog.dto;

import com.oskott.dogtrainerbackend.dog.entity.Dog;
import com.oskott.dogtrainerbackend.dog.entity.DogMediaType;

import java.util.UUID;

/**
 * A dog as it appears in a listing of another user's dogs (e.g. on their public profile) -
 * intentionally minimal, see {@link PublicDogResponse} for the full public profile shape.
 */
public record PublicDogSummaryResponse(
        UUID id,
        String name,
        String breed,
        String mediaUrl,
        DogMediaType mediaType
) {

    public static PublicDogSummaryResponse from(Dog dog) {
        return new PublicDogSummaryResponse(
                dog.getId(),
                dog.getName(),
                dog.getBreed(),
                dog.getMediaUrl(),
                dog.getMediaType()
        );
    }
}
