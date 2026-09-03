package com.oskott.dogtrainerbackend.dog.service;

import com.oskott.dogtrainerbackend.common.exception.AccessDeniedForResourceException;
import com.oskott.dogtrainerbackend.common.exception.ResourceNotFoundException;
import com.oskott.dogtrainerbackend.common.security.CurrentUserProvider;
import com.oskott.dogtrainerbackend.dog.dto.DogRequest;
import com.oskott.dogtrainerbackend.dog.dto.DogResponse;
import com.oskott.dogtrainerbackend.dog.entity.Dog;
import com.oskott.dogtrainerbackend.dog.repository.DogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DogService {

    private final DogRepository dogRepository;
    private final CurrentUserProvider currentUserProvider;

    public DogService(DogRepository dogRepository, CurrentUserProvider currentUserProvider) {
        this.dogRepository = dogRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public List<DogResponse> listDogs() {
        UUID ownerId = currentUserProvider.getCurrentUserId();
        return dogRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
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
        Dog dog = new Dog(
                UUID.randomUUID(),
                ownerId,
                request.name(),
                request.breed(),
                request.birthDate(),
                request.sex(),
                request.weight(),
                request.imageUrl(),
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
        dog.setImageUrl(request.imageUrl());
        return DogResponse.from(dog);
    }

    @Transactional
    public void deleteDog(UUID dogId) {
        Dog dog = getOwnedDog(dogId);
        dogRepository.delete(dog);
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
