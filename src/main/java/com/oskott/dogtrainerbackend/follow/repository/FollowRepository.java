package com.oskott.dogtrainerbackend.follow.repository;

import com.oskott.dogtrainerbackend.follow.entity.Follow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowRepository extends JpaRepository<Follow, UUID> {

    boolean existsByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);

    Optional<Follow> findByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);

    List<Follow> findAllByFollowerIdOrderByCreatedAtDesc(UUID followerId);

    List<Follow> findAllByFolloweeIdOrderByCreatedAtDesc(UUID followeeId);
}
