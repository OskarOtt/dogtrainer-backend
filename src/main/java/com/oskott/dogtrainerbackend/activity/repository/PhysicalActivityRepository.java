package com.oskott.dogtrainerbackend.activity.repository;

import com.oskott.dogtrainerbackend.activity.entity.PhysicalActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PhysicalActivityRepository extends JpaRepository<PhysicalActivity, UUID> {

    @Query("""
            SELECT a FROM PhysicalActivity a
            JOIN a.dogIds d
            WHERE d = :dogId
            ORDER BY a.startedAt DESC
            """)
    List<PhysicalActivity> findAllByDogIdOrderByStartedAtDesc(@Param("dogId") UUID dogId);
}
