package com.oskott.dogtrainerbackend.dog.service;

import com.oskott.dogtrainerbackend.common.exception.ResourceNotFoundException;
import com.oskott.dogtrainerbackend.common.security.CurrentUserProvider;
import com.oskott.dogtrainerbackend.dog.dto.PublicDogResponse;
import com.oskott.dogtrainerbackend.dog.dto.PublicDogSummaryResponse;
import com.oskott.dogtrainerbackend.dog.dto.PublicSessionSummaryResponse;
import com.oskott.dogtrainerbackend.dog.entity.Dog;
import com.oskott.dogtrainerbackend.dog.repository.DogRepository;
import com.oskott.dogtrainerbackend.moderation.service.ModerationService;
import com.oskott.dogtrainerbackend.title.dto.DogTitleResponse;
import com.oskott.dogtrainerbackend.title.repository.DogTitleRepository;
import com.oskott.dogtrainerbackend.training.entity.SessionStatus;
import com.oskott.dogtrainerbackend.training.repository.TrainingSessionRepository;
import com.oskott.dogtrainerbackend.user.entity.User;
import com.oskott.dogtrainerbackend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read-only dog profile data as seen by other users (e.g. from a post's dog tag, or a user's
 * public profile) - kept separate from {@link DogService} since it aggregates across dog,
 * title, training-session and user data (mirrors how {@code DogStatisticsService} is split out
 * for the same reason), and returns a deliberately trimmed-down shape (no weight, no session
 * location/notes/exercises).
 * <p>
 * Blocked users are hidden as if the dog doesn't exist, matching how {@code PostService} hides
 * posts across a block relationship, rather than a 403 that would reveal the block.
 */
@Service
@Transactional(readOnly = true)
public class PublicDogProfileService {

    private static final int RECENT_SESSIONS_LIMIT = 10;

    private final DogRepository dogRepository;
    private final UserRepository userRepository;
    private final DogTitleRepository dogTitleRepository;
    private final TrainingSessionRepository trainingSessionRepository;
    private final ModerationService moderationService;
    private final CurrentUserProvider currentUserProvider;

    public PublicDogProfileService(
            DogRepository dogRepository,
            UserRepository userRepository,
            DogTitleRepository dogTitleRepository,
            TrainingSessionRepository trainingSessionRepository,
            ModerationService moderationService,
            CurrentUserProvider currentUserProvider
    ) {
        this.dogRepository = dogRepository;
        this.userRepository = userRepository;
        this.dogTitleRepository = dogTitleRepository;
        this.trainingSessionRepository = trainingSessionRepository;
        this.moderationService = moderationService;
        this.currentUserProvider = currentUserProvider;
    }

    public PublicDogResponse getPublicDog(UUID dogId) {
        Dog dog = dogRepository.findById(dogId)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("Dog", dogId));
        requireNotBlockedDog(dog);

        User owner = userRepository.findById(dog.getOwnerId())
                .orElseThrow(() -> ResourceNotFoundException.forEntity("User", dog.getOwnerId()));

        List<DogTitleResponse> titles = dogTitleRepository
                .findAllByDogIdOrderByDateEarnedDescCreatedAtDesc(dogId).stream()
                .map(DogTitleResponse::from)
                .toList();
        List<PublicSessionSummaryResponse> recentSessions = trainingSessionRepository
                .findTop10ByDogIdOrderByStartedAtDesc(dogId).stream()
                .limit(RECENT_SESSIONS_LIMIT)
                .map(PublicSessionSummaryResponse::from)
                .toList();
        long completedSessionCount = trainingSessionRepository.countByDogIdAndStatus(dogId, SessionStatus.COMPLETED);

        return PublicDogResponse.from(dog, owner.getName(), owner.getAvatarUrl(), completedSessionCount, titles, recentSessions);
    }

    public List<PublicDogSummaryResponse> listPublicDogs(UUID ownerId) {
        if (!userRepository.existsById(ownerId)) {
            throw ResourceNotFoundException.forEntity("User", ownerId);
        }
        requireNotBlockedUser(ownerId);
        return dogRepository.findAllByOwnerIdOrderBySortOrderAsc(ownerId).stream()
                .map(PublicDogSummaryResponse::from)
                .toList();
    }

    /**
     * Throws as if the dog doesn't exist when the current user and the dog's owner have a block
     * relationship in either direction. No-op for a user viewing their own dog.
     */
    private void requireNotBlockedDog(Dog dog) {
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        if (!currentUserId.equals(dog.getOwnerId()) && moderationService.isBlockedEitherWay(currentUserId, dog.getOwnerId())) {
            throw ResourceNotFoundException.forEntity("Dog", dog.getId());
        }
    }

    /**
     * Throws as if the user doesn't exist when the current user and {@code ownerId} have a block
     * relationship in either direction. No-op for a user viewing their own dog list.
     */
    private void requireNotBlockedUser(UUID ownerId) {
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        if (!currentUserId.equals(ownerId) && moderationService.isBlockedEitherWay(currentUserId, ownerId)) {
            throw ResourceNotFoundException.forEntity("User", ownerId);
        }
    }
}
