package com.oskott.dogtrainerbackend.dog.repository;

import com.oskott.dogtrainerbackend.dog.entity.Dog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DogRepository extends JpaRepository<Dog, UUID> {

    List<Dog> findAllByOwnerIdOrderBySortOrderAsc(UUID ownerId);

    Optional<Dog> findTopByOwnerIdOrderBySortOrderDesc(UUID ownerId);
}
