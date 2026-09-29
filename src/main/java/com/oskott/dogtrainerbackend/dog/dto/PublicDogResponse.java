package com.oskott.dogtrainerbackend.dog.dto;

import com.oskott.dogtrainerbackend.dog.entity.Dog;
import com.oskott.dogtrainerbackend.dog.entity.DogMediaType;
import com.oskott.dogtrainerbackend.dog.entity.Sex;
import com.oskott.dogtrainerbackend.title.dto.DogTitleResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A dog's profile as seen by other users. Unlike {@link DogResponse} (owner-only), this adds
 * owner attribution and a trimmed session history/count, and deliberately omits weight (private
 * to the owner).
 */
public record PublicDogResponse(
        UUID id,
        UUID ownerId,
        String ownerName,
        String ownerAvatarUrl,
        String name,
        String breed,
        LocalDate birthDate,
        Sex sex,
        String mediaUrl,
        DogMediaType mediaType,
        Instant createdAt,
        long completedSessionCount,
        List<DogTitleResponse> titles,
        List<PublicSessionSummaryResponse> recentSessions
) {

    public static PublicDogResponse from(
            Dog dog,
            String ownerName,
            String ownerAvatarUrl,
            long completedSessionCount,
            List<DogTitleResponse> titles,
            List<PublicSessionSummaryResponse> recentSessions
    ) {
        return new PublicDogResponse(
                dog.getId(),
                dog.getOwnerId(),
                ownerName,
                ownerAvatarUrl,
                dog.getName(),
                dog.getBreed(),
                dog.getBirthDate(),
                dog.getSex(),
                dog.getMediaUrl(),
                dog.getMediaType(),
                dog.getCreatedAt(),
                completedSessionCount,
                titles,
                recentSessions
        );
    }
}
