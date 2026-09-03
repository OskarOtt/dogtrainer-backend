package com.oskott.dogtrainerbackend.dog.controller;

import com.oskott.dogtrainerbackend.dog.dto.DogRequest;
import com.oskott.dogtrainerbackend.dog.dto.DogResponse;
import com.oskott.dogtrainerbackend.dog.service.DogService;
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
@RequestMapping("/api/v1/dogs")
public class DogController {

    private final DogService dogService;

    public DogController(DogService dogService) {
        this.dogService = dogService;
    }

    @GetMapping
    public List<DogResponse> listDogs() {
        return dogService.listDogs();
    }

    @PostMapping
    public ResponseEntity<DogResponse> createDog(@Valid @RequestBody DogRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dogService.createDog(request));
    }

    @GetMapping("/{id}")
    public DogResponse getDog(@PathVariable UUID id) {
        return dogService.getDog(id);
    }

    @PutMapping("/{id}")
    public DogResponse updateDog(@PathVariable UUID id, @Valid @RequestBody DogRequest request) {
        return dogService.updateDog(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDog(@PathVariable UUID id) {
        dogService.deleteDog(id);
        return ResponseEntity.noContent().build();
    }
}
