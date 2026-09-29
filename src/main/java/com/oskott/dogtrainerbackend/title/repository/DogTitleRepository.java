package com.oskott.dogtrainerbackend.title.repository;

import com.oskott.dogtrainerbackend.title.entity.DogTitle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DogTitleRepository extends JpaRepository<DogTitle, UUID> {

    @Query("SELECT dt FROM DogTitle dt WHERE dt.dogId = :dogId "
            + "ORDER BY dt.dateEarned DESC NULLS LAST, dt.createdAt DESC")
    List<DogTitle> findAllByDogIdOrderByDateEarnedDescCreatedAtDesc(@Param("dogId") UUID dogId);
}
