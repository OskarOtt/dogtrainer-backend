package com.oskott.dogtrainerbackend.title.service;

import com.oskott.dogtrainerbackend.common.exception.ResourceNotFoundException;
import com.oskott.dogtrainerbackend.dog.service.DogService;
import com.oskott.dogtrainerbackend.title.dto.DogTitleRequest;
import com.oskott.dogtrainerbackend.title.dto.DogTitleResponse;
import com.oskott.dogtrainerbackend.title.entity.DogTitle;
import com.oskott.dogtrainerbackend.title.repository.DogTitleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DogTitleService {

    private final DogTitleRepository dogTitleRepository;
    private final DogService dogService;

    public DogTitleService(DogTitleRepository dogTitleRepository, DogService dogService) {
        this.dogTitleRepository = dogTitleRepository;
        this.dogService = dogService;
    }

    @Transactional(readOnly = true)
    public List<DogTitleResponse> listTitlesForDog(UUID dogId) {
        dogService.getOwnedDog(dogId);
        return dogTitleRepository.findAllByDogIdOrderByDateEarnedDescCreatedAtDesc(dogId).stream()
                .map(DogTitleResponse::from)
                .toList();
    }

    @Transactional
    public DogTitleResponse createTitle(UUID dogId, DogTitleRequest request) {
        dogService.getOwnedDog(dogId);
        DogTitle dogTitle = new DogTitle(
                UUID.randomUUID(),
                dogId,
                request.title(),
                request.dateEarned(),
                Instant.now()
        );
        dogTitleRepository.save(dogTitle);
        return DogTitleResponse.from(dogTitle);
    }

    @Transactional
    public DogTitleResponse updateTitle(UUID titleId, DogTitleRequest request) {
        DogTitle dogTitle = getOwnedTitle(titleId);
        dogTitle.setTitle(request.title());
        dogTitle.setDateEarned(request.dateEarned());
        return DogTitleResponse.from(dogTitle);
    }

    @Transactional
    public void deleteTitle(UUID titleId) {
        DogTitle dogTitle = getOwnedTitle(titleId);
        dogTitleRepository.delete(dogTitle);
    }

    /**
     * Fetches a title and enforces that its dog belongs to the currently authenticated user.
     */
    private DogTitle getOwnedTitle(UUID titleId) {
        DogTitle dogTitle = dogTitleRepository.findById(titleId)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("DogTitle", titleId));
        // Throws AccessDeniedForResourceException if the dog isn't owned by the current user.
        dogService.getOwnedDog(dogTitle.getDogId());
        return dogTitle;
    }
}
