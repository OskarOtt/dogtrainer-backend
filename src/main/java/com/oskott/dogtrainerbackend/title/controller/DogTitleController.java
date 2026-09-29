package com.oskott.dogtrainerbackend.title.controller;

import com.oskott.dogtrainerbackend.title.dto.DogTitleRequest;
import com.oskott.dogtrainerbackend.title.dto.DogTitleResponse;
import com.oskott.dogtrainerbackend.title.service.DogTitleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class DogTitleController {

    private final DogTitleService dogTitleService;

    public DogTitleController(DogTitleService dogTitleService) {
        this.dogTitleService = dogTitleService;
    }

    @GetMapping("/dogs/{dogId}/titles")
    public List<DogTitleResponse> listTitles(@PathVariable UUID dogId) {
        return dogTitleService.listTitlesForDog(dogId);
    }

    @PostMapping("/dogs/{dogId}/titles")
    public ResponseEntity<DogTitleResponse> createTitle(@PathVariable UUID dogId, @Valid @RequestBody DogTitleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dogTitleService.createTitle(dogId, request));
    }

    @PutMapping("/titles/{id}")
    public DogTitleResponse updateTitle(@PathVariable UUID id, @Valid @RequestBody DogTitleRequest request) {
        return dogTitleService.updateTitle(id, request);
    }

    @DeleteMapping("/titles/{id}")
    public ResponseEntity<Void> deleteTitle(@PathVariable UUID id) {
        dogTitleService.deleteTitle(id);
        return ResponseEntity.noContent().build();
    }
}
