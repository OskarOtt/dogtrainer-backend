package com.oskott.dogtrainerbackend.user.service;

import com.oskott.dogtrainerbackend.common.exception.AccessDeniedForResourceException;
import com.oskott.dogtrainerbackend.common.exception.ResourceNotFoundException;
import com.oskott.dogtrainerbackend.common.security.CurrentUserProvider;
import com.oskott.dogtrainerbackend.storage.MediaCategory;
import com.oskott.dogtrainerbackend.storage.StorageService;
import com.oskott.dogtrainerbackend.storage.dto.UploadUrlRequest;
import com.oskott.dogtrainerbackend.storage.dto.UploadUrlResponse;
import com.oskott.dogtrainerbackend.user.dto.AvatarConfirmRequest;
import com.oskott.dogtrainerbackend.user.dto.PublicUserResponse;
import com.oskott.dogtrainerbackend.user.dto.UserResponse;
import com.oskott.dogtrainerbackend.user.entity.User;
import com.oskott.dogtrainerbackend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final StorageService storageService;

    public UserService(UserRepository userRepository, CurrentUserProvider currentUserProvider, StorageService storageService) {
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.storageService = storageService;
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
        storageService.deleteObjectIfPresent(storageService.extractObjectKey(user.getAvatarUrl()));
        user.setAvatarUrl(storageService.buildPublicUrl(objectKey));
        return UserResponse.from(user);
    }

    @Transactional
    public void deleteAvatar() {
        User user = getCurrentUser();
        storageService.deleteObjectIfPresent(storageService.extractObjectKey(user.getAvatarUrl()));
        user.setAvatarUrl(null);
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
