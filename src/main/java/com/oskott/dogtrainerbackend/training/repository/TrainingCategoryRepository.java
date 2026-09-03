package com.oskott.dogtrainerbackend.training.repository;

import com.oskott.dogtrainerbackend.training.entity.TrainingCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TrainingCategoryRepository extends JpaRepository<TrainingCategory, UUID> {

    List<TrainingCategory> findAllByOrderByDisplayOrderAsc();
}
