package com.oskott.dogtrainerbackend.activity.repository;

import com.oskott.dogtrainerbackend.activity.entity.PhysicalActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PhysicalActivityRepository extends JpaRepository<PhysicalActivity, UUID> {

    List<PhysicalActivity> findAllByDogIdOrderByStartedAtDesc(UUID dogId);
}
