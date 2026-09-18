package com.oskott.dogtrainerbackend.user.service;

import com.oskott.dogtrainerbackend.auth.repository.RefreshTokenRepository;
import com.oskott.dogtrainerbackend.common.exception.AccessDeniedForResourceException;
import com.oskott.dogtrainerbackend.common.exception.AuthenticationFailedException;
import com.oskott.dogtrainerbackend.common.exception.ResourceNotFoundException;
import com.oskott.dogtrainerbackend.common.security.CurrentUserProvider;
import com.oskott.dogtrainerbackend.dog.entity.Dog;
import com.oskott.dogtrainerbackend.dog.repository.DogRepository;
import com.oskott.dogtrainerbackend.follow.repository.FollowRepository;
import com.oskott.dogtrainerbackend.storage.ImageProcessingService;
import com.oskott.dogtrainerbackend.storage.MediaCategory;
import com.oskott.dogtrainerbackend.storage.StorageService;
import com.oskott.dogtrainerbackend.storage.dto.UploadUrlRequest;
import com.oskott.dogtrainerbackend.storage.dto.UploadUrlResponse;
import com.oskott.dogtrainerbackend.user.dto.AvatarConfirmRequest;
import com.oskott.dogtrainerbackend.user.dto.PublicUserResponse;
import com.oskott.dogtrainerbackend.user.dto.UserResponse;
import com.oskott.dogtrainerbackend.user.entity.User;
import com.oskott.dogtrainerbackend.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final StorageService storageService;
    private final DogRepository dogRepository;
    private final FollowRepository followRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            CurrentUserProvider currentUserProvider,
            StorageService storageService,
            DogRepository dogRepository,
            FollowRepository followRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.storageService = storageService;
        this.dogRepository = dogRepository;
        this.followRepository = followRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PublicUserResponse getUser(UUID userId) {
        return userRepository.findById(userId)
                .map(PublicUserResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("User", userId));
    }

    @Transactional(readOnly = true)
    public PublicUserResponse getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(PublicUserResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("User", email));
    }

    public UploadUrlResponse createAvatarUploadUrl(UploadUrlRequest request) {
        UUID userId = currentUserProvider.getCurrentUserId();
        return storageService.createUploadUrl(avatarKeyPrefix(userId), MediaCategory.IMAGE, request);
    }

    @Transactional
    public UserResponse confirmAvatar(AvatarConfirmRequest request) {
        User user = getCurrentUser();
        String objectKey = request.objectKey();
        requireOwnedKey(objectKey, user.getId());
        if (!storageService.objectExists(objectKey)) {
            throw ResourceNotFoundException.forEntity("Object", objectKey);
        }
        String resizedKey = storageService.resizeStoredImage(objectKey, ImageProcessingService.AVATAR_DOG_MAX_DIMENSION);
        storageService.deleteObjectIfPresent(storageService.extractObjectKey(user.getAvatarUrl()));
        user.setAvatarUrl(storageService.buildPublicUrl(resizedKey));
        return UserResponse.from(user);
    }

    @Transactional
    public void deleteAvatar() {
        User user = getCurrentUser();
        storageService.deleteObjectIfPresent(storageService.extractObjectKey(user.getAvatarUrl()));
        user.setAvatarUrl(null);
    }

    /**
     * Soft-deletes the account: the user row is kept (anonymized) rather than removed so posts
     * stay attributable to "Deleted User", but everything private to the account - dogs and
     * everything hanging off a dog (goals, training sessions/plans), follows, refresh tokens - is
     * hard-deleted. Requires the current password as a safety check.
     */
    @Transactional
    public void deleteAccount(String password) {
        User user = getCurrentUser();
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new AuthenticationFailedException("Incorrect password");
        }
        UUID userId = user.getId();

        List<Dog> dogs = dogRepository.findAllByOwnerIdOrderBySortOrderAsc(userId);
        dogs.forEach(dog -> storageService.deleteObjectIfPresent(storageService.extractObjectKey(dog.getMediaUrl())));
        // Cascades in the DB to goals/training_sessions/session_exercises/training_plans/
        // training_plan_exercises, and SET NULLs posts.dog_id/posts.training_session_id.
        dogRepository.deleteAll(dogs);

        followRepository.deleteAll(followRepository.findAllByFollowerIdOrderByCreatedAtDesc(userId));
        followRepository.deleteAll(followRepository.findAllByFolloweeIdOrderByCreatedAtDesc(userId));
        refreshTokenRepository.deleteByUserId(userId);
        storageService.deleteObjectIfPresent(storageService.extractObjectKey(user.getAvatarUrl()));

        user.setEmail("deleted-" + userId + "@deleted.dogtrainer.app");
        user.setName("Deleted User");
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setAvatarUrl(null);
        user.setDeletedAt(Instant.now());
    }

    private User getCurrentUser() {
        UUID userId = currentUserProvider.getCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("User", userId));
    }

    private void requireOwnedKey(String objectKey, UUID userId) {
        if (!objectKey.startsWith(avatarKeyPrefix(userId) + "/")) {
            throw new AccessDeniedForResourceException("You do not have access to this object");
        }
    }

    private String avatarKeyPrefix(UUID userId) {
        return "avatars/" + userId;
    }
}
