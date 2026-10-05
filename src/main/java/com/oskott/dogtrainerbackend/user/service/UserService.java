package com.oskott.dogtrainerbackend.user.service;

import com.oskott.dogtrainerbackend.auth.dto.AuthMethod;
import com.oskott.dogtrainerbackend.auth.entity.ExternalAuthProvider;
import com.oskott.dogtrainerbackend.auth.entity.ExternalIdentity;
import com.oskott.dogtrainerbackend.auth.repository.ExternalIdentityRepository;
import com.oskott.dogtrainerbackend.auth.repository.RefreshTokenRepository;
import com.oskott.dogtrainerbackend.auth.service.AppleTokenRevoker;
import com.oskott.dogtrainerbackend.auth.service.ExternalIdentityVerifier;
import com.oskott.dogtrainerbackend.auth.service.VerifiedExternalIdentity;
import com.oskott.dogtrainerbackend.common.exception.AuthFlowException;
import com.oskott.dogtrainerbackend.common.exception.AccessDeniedForResourceException;
import com.oskott.dogtrainerbackend.common.exception.AuthenticationFailedException;
import com.oskott.dogtrainerbackend.common.exception.BusinessRuleException;
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
import com.oskott.dogtrainerbackend.user.dto.DeleteAccountRequest;
import com.oskott.dogtrainerbackend.user.dto.PublicUserResponse;
import com.oskott.dogtrainerbackend.user.dto.UpdateDisplayNameRequest;
import com.oskott.dogtrainerbackend.user.dto.UserResponse;
import com.oskott.dogtrainerbackend.user.dto.UserSearchResult;
import com.oskott.dogtrainerbackend.user.entity.User;
import com.oskott.dogtrainerbackend.user.repository.UserRepository;
import com.oskott.dogtrainerbackend.user.repository.UserSearchRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private static final int MIN_SEARCH_QUERY_LENGTH = 2;
    private static final int DEFAULT_SEARCH_LIMIT = 20;
    private static final int MAX_SEARCH_LIMIT = 50;

    private final UserRepository userRepository;
    private final UserSearchRepository userSearchRepository;
    private final CurrentUserProvider currentUserProvider;
    private final StorageService storageService;
    private final DogRepository dogRepository;
    private final FollowRepository followRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ExternalIdentityRepository externalIdentityRepository;
    private final ExternalIdentityVerifier externalIdentityVerifier;
    private final AppleTokenRevoker appleTokenRevoker;
    private final long reauthenticationMaxAgeSeconds;

    public UserService(
            UserRepository userRepository,
            UserSearchRepository userSearchRepository,
            CurrentUserProvider currentUserProvider,
            StorageService storageService,
            DogRepository dogRepository,
            FollowRepository followRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            ExternalIdentityRepository externalIdentityRepository,
            ExternalIdentityVerifier externalIdentityVerifier,
            AppleTokenRevoker appleTokenRevoker,
            @Value("${app.auth.reauthentication-max-age-seconds:300}") long reauthenticationMaxAgeSeconds
    ) {
        this.userRepository = userRepository;
        this.userSearchRepository = userSearchRepository;
        this.currentUserProvider = currentUserProvider;
        this.storageService = storageService;
        this.dogRepository = dogRepository;
        this.followRepository = followRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.externalIdentityRepository = externalIdentityRepository;
        this.externalIdentityVerifier = externalIdentityVerifier;
        this.appleTokenRevoker = appleTokenRevoker;
        this.reauthenticationMaxAgeSeconds = reauthenticationMaxAgeSeconds;
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

    @Transactional(readOnly = true)
    public List<UserSearchResult> searchUsers(String query, Integer limit) {
        String trimmedQuery = query == null ? "" : query.trim();
        if (trimmedQuery.length() < MIN_SEARCH_QUERY_LENGTH) {
            throw new BusinessRuleException(
                    "Search query must be at least " + MIN_SEARCH_QUERY_LENGTH + " characters"
            );
        }
        int effectiveLimit = limit == null
                ? DEFAULT_SEARCH_LIMIT
                : Math.min(Math.max(limit, 1), MAX_SEARCH_LIMIT);
        // Exclude the current user so they can't find/follow themselves from search results.
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        return userSearchRepository.search(trimmedQuery, effectiveLimit, currentUserId).stream()
                .map(UserSearchResult::from)
                .toList();
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
        return UserResponse.from(user, externalIdentityRepository.findAllByUserId(user.getId()));
    }

    @Transactional
    public UserResponse updateDisplayName(UpdateDisplayNameRequest request) {
        User user = getCurrentUser();
        user.setName(request.name().trim());
        return UserResponse.from(user, externalIdentityRepository.findAllByUserId(user.getId()));
    }

    @Transactional
    public void deleteAvatar() {
        User user = getCurrentUser();
        storageService.deleteObjectIfPresent(storageService.extractObjectKey(user.getAvatarUrl()));
        user.setAvatarUrl(null);
    }

    @Transactional
    public void deleteAccount(DeleteAccountRequest request) {
        User user = getCurrentUser();
        verifyDeletion(request, user);
        UUID userId = user.getId();

        List<Dog> dogs = dogRepository.findAllByOwnerIdOrderBySortOrderAsc(userId);
        dogs.forEach(dog -> storageService.deleteObjectIfPresent(storageService.extractObjectKey(dog.getMediaUrl())));
        // Cascades in the DB to goals/training_sessions/session_exercises/training_plans/
        // training_plan_exercises, and SET NULLs posts.dog_id/posts.training_session_id.
        dogRepository.deleteAll(dogs);

        followRepository.deleteAll(followRepository.findAllByFollowerIdOrderByCreatedAtDesc(userId));
        followRepository.deleteAll(followRepository.findAllByFolloweeIdOrderByCreatedAtDesc(userId));
        refreshTokenRepository.deleteByUserId(userId);
        externalIdentityRepository.deleteByUserId(userId);
        storageService.deleteObjectIfPresent(storageService.extractObjectKey(user.getAvatarUrl()));

        user.setEmail("deleted-" + userId + "@deleted.dogtrainer.app");
        user.setName("Deleted User");
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setAvatarUrl(null);
        user.setDeletedAt(Instant.now());
    }

    private void verifyDeletion(DeleteAccountRequest request, User user) {
        AuthMethod method = request.method() == null && request.password() != null
                ? AuthMethod.PASSWORD
                : request.method();
        if (method == null) {
            throw new AuthFlowException(
                    HttpStatus.BAD_REQUEST,
                    "DELETION_CONFIRMATION_REQUIRED",
                    "Choose a sign-in method to confirm account deletion"
            );
        }
        if (method == AuthMethod.PASSWORD) {
            if (!user.hasPassword()
                    || request.password() == null
                    || !passwordEncoder.matches(request.password(), user.getPassword())) {
                throw new AuthenticationFailedException("Incorrect password");
            }
            return;
        }
        if (request.idToken() == null || request.idToken().isBlank()) {
            throw new AuthenticationFailedException("Fresh provider authentication is required");
        }

        ExternalAuthProvider provider = ExternalAuthProvider.valueOf(method.name());
        VerifiedExternalIdentity verified = externalIdentityVerifier.verify(provider, request.idToken());
        Instant oldestAccepted = Instant.now().minusSeconds(reauthenticationMaxAgeSeconds);
        if (verified.issuedAt() == null || verified.issuedAt().isBefore(oldestAccepted)) {
            throw new AuthenticationFailedException("Provider authentication is too old");
        }
        ExternalIdentity identity = externalIdentityRepository
                .findByProviderAndProviderSubject(provider, verified.subject())
                .filter(candidate -> candidate.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new AuthenticationFailedException(
                        "Provider account is not linked to this user"
                ));

        if (identity.getProvider() == ExternalAuthProvider.APPLE) {
            if (request.authorizationCode() == null || request.authorizationCode().isBlank()) {
                throw new AuthenticationFailedException("Apple authorization code is required");
            }
            appleTokenRevoker.revoke(request.authorizationCode(), verified.subject());
        }
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
