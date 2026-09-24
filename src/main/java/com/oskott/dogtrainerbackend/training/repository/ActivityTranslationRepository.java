package com.oskott.dogtrainerbackend.training.repository;

import com.oskott.dogtrainerbackend.training.entity.ActivityTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ActivityTranslationRepository extends JpaRepository<ActivityTranslation, UUID> {

    List<ActivityTranslation> findAllByLocaleAndActivityIdIn(String locale, Collection<UUID> activityIds);
}
