package com.oskott.dogtrainerbackend.training.repository;

import com.oskott.dogtrainerbackend.training.entity.TrainingCategoryTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TrainingCategoryTranslationRepository extends JpaRepository<TrainingCategoryTranslation, UUID> {

    List<TrainingCategoryTranslation> findAllByLocaleAndCategoryIdIn(String locale, Collection<UUID> categoryIds);
}
