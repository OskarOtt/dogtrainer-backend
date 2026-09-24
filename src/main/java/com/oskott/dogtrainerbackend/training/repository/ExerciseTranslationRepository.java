package com.oskott.dogtrainerbackend.training.repository;

import com.oskott.dogtrainerbackend.training.entity.ExerciseTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ExerciseTranslationRepository extends JpaRepository<ExerciseTranslation, UUID> {

    List<ExerciseTranslation> findAllByLocaleAndExerciseIdIn(String locale, Collection<UUID> exerciseIds);
}
