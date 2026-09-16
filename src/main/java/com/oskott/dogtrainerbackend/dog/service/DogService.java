package com.oskott.dogtrainerbackend.dog.service;

import com.oskott.dogtrainerbackend.common.exception.AccessDeniedForResourceException;
import com.oskott.dogtrainerbackend.common.exception.BusinessRuleException;
import com.oskott.dogtrainerbackend.common.exception.InvalidFileException;
import com.oskott.dogtrainerbackend.common.exception.ResourceNotFoundException;
import com.oskott.dogtrainerbackend.common.security.CurrentUserProvider;
import com.oskott.dogtrainerbackend.dog.dto.DogMediaConfirmRequest;
import com.oskott.dogtrainerbackend.dog.dto.DogRequest;
import com.oskott.dogtrainerbackend.dog.dto.DogResponse;
import com.oskott.dogtrainerbackend.dog.entity.Dog;
import com.oskott.dogtrainerbackend.dog.entity.DogMediaType;
import com.oskott.dogtrainerbackend.dog.repository.DogRepository;
import com.oskott.dogtrainerbackend.storage.ImageProcessingService;
import com.oskott.dogtrainerbackend.storage.MediaCategory;
import com.oskott.dogtrainerbackend.storage.StorageService;
import com.oskott.dogtrainerbackend.storage.dto.UploadUrlRequest;
import com.oskott.dogtrainerbackend.storage.dto.UploadUrlResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DogService {

    private final DogRepository dogRepository;
    private final CurrentUserProvider currentUserProvider;
    private final StorageService storageService;

    public DogService(DogRepository dogRepository, CurrentUserProvider currentUserProvider, StorageService storageService) {
        this.dogRepository = dogRepository;
        this.currentUserProvider = currentUserProvider;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public List<DogResponse> listDogs() {
        UUID ownerId = currentUserProvider.getCurrentUserId();
        return dogRepository.findAllByOwnerIdOrderBySortOrderAsc(ownerId).stream()
                .map(DogResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DogResponse getDog(UUID dogId) {
        return DogResponse.from(getOwnedDog(dogId));
    }

    @Transactional
    public DogResponse createDog(DogRequest request) {
        UUID ownerId = currentUserProvider.getCurrentUserId();
        int nextSortOrder = dogRepository.findTopByOwnerIdOrderBySortOrderDesc(ownerId)
                .map(dog -> dog.getSortOrder() + 1)
                .orElse(0);
        Dog dog = new Dog(
                UUID.randomUUID(),
                ownerId,
                request.name(),
                request.breed(),
                request.birthDate(),
                request.sex(),
                request.weight(),
                null,
                null,
                nextSortOrder,
                Instant.now()
        );
        dogRepository.save(dog);
        return DogResponse.from(dog);
    }

    @Transactional
    public DogResponse updateDog(UUID dogId, DogRequest request) {
        Dog dog = getOwnedDog(dogId);
        dog.setName(request.name());
        dog.setBreed(request.breed());
        dog.setBirthDate(request.birthDate());
        dog.setSex(request.sex());
        dog.setWeight(request.weight());
        return DogResponse.from(dog);
    }

    @Transactional
    public void deleteDog(UUID dogId) {
        Dog dog = getOwnedDog(dogId);
        dogRepository.delete(dog);
    }

    /**
     * Reassigns sort order for all of the current user's dogs to match {@code orderedDogIds}
     * (index 0 = top of the Dogs tab = first everywhere else in the app). The given list must
     * contain exactly the same set of dog ids the user currently owns — no more, no fewer.
     */
    @Transactional
    public List<DogResponse> reorderDogs(List<UUID> orderedDogIds) {
        UUID ownerId = currentUserProvider.getCurrentUserId();
        List<Dog> ownedDogs = dogRepository.findAllByOwnerIdOrderBySortOrderAsc(ownerId);

        Set<UUID> ownedIds = ownedDogs.stream().map(Dog::getId).collect(java.util.stream.Collectors.toSet());
        Set<UUID> requestedIds = Set.copyOf(orderedDogIds);
        if (!ownedIds.equals(requestedIds) || orderedDogIds.size() != ownedDogs.size()) {
            throw new BusinessRuleException("dogIds must contain exactly the current user's dogs, each exactly once");
        }

        Map<UUID, Dog> dogsById = new HashMap<>();
        ownedDogs.forEach(dog -> dogsById.put(dog.getId(), dog));

        for (int index = 0; index < orderedDogIds.size(); index++) {
            dogsById.get(orderedDogIds.get(index)).setSortOrder(index);
        }

        return orderedDogIds.stream()
                .map(id -> DogResponse.from(dogsById.get(id)))
                .toList();
    }

    public UploadUrlResponse createMediaUploadUrl(UUID dogId, UploadUrlRequest request) {
        getOwnedDog(dogId);
        MediaCategory category = MediaCategory.fromContentType(request.contentType())
                .orElseThrow(() -> new InvalidFileException("Unsupported content type: " + request.contentType()));
        return storageService.createUploadUrl(mediaKeyPrefix(dogId), category, request);
    }

    @Transactional
    public DogResponse confirmMedia(UUID dogId, DogMediaConfirmRequest request) {
        Dog dog = getOwnedDog(dogId);
        String objectKey = request.objectKey();
        if (!objectKey.startsWith(mediaKeyPrefix(dogId) + "/")) {
            throw new AccessDeniedForResourceException("You do not have access to this object");
        }
        if (!storageService.objectExists(objectKey)) {
            throw ResourceNotFoundException.forEntity("Object", objectKey);
        }
        String extension = objectKey.substring(objectKey.lastIndexOf('.') + 1);
        DogMediaType mediaType = MediaCategory.fromExtension(extension)
                .map(category -> category == MediaCategory.VIDEO ? DogMediaType.VIDEO : DogMediaType.IMAGE)
                .orElseThrow(() -> new InvalidFileException("Unrecognized media file extension: " + extension));

        String finalObjectKey = mediaType == DogMediaType.IMAGE
                ? storageService.resizeStoredImage(objectKey, ImageProcessingService.AVATAR_DOG_MAX_DIMENSION)
                : objectKey;

        storageService.deleteObjectIfPresent(storageService.extractObjectKey(dog.getMediaUrl()));
        dog.setMediaUrl(storageService.buildPublicUrl(finalObjectKey));
        dog.setMediaType(mediaType);
        return DogResponse.from(dog);
    }

    @Transactional
    public void deleteMedia(UUID dogId) {
        Dog dog = getOwnedDog(dogId);
        storageService.deleteObjectIfPresent(storageService.extractObjectKey(dog.getMediaUrl()));
        dog.setMediaUrl(null);
        dog.setMediaType(null);
    }

    private String mediaKeyPrefix(UUID dogId) {
        return "dogs/" + dogId;
    }

    /**
     * Fetches a dog and enforces that it belongs to the currently authenticated user, so that
     * no user can ever read or modify another user's dog (or its associated training data).
     */
    public Dog getOwnedDog(UUID dogId) {
        Dog dog = dogRepository.findById(dogId)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("Dog", dogId));
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        if (!dog.getOwnerId().equals(currentUserId)) {
            throw new AccessDeniedForResourceException("You do not have access to this dog");
        }
        return dog;
    }
}
