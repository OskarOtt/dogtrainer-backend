package com.oskott.dogtrainerbackend.dog.repository;

import com.oskott.dogtrainerbackend.dog.entity.Dog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DogRepository extends JpaRepository<Dog, UUID> {

    List<Dog> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
