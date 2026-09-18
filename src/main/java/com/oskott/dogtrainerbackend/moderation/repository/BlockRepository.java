package com.oskott.dogtrainerbackend.moderation.repository;

import com.oskott.dogtrainerbackend.moderation.entity.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlockRepository extends JpaRepository<Block, UUID> {

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    Optional<Block> findByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    List<Block> findAllByBlockerIdOrderByCreatedAtDesc(UUID blockerId);

    /**
     * All user ids that {@code userId} has blocked or has been blocked by - i.e. both sides of
     * every block relationship {@code userId} is part of. Used to hide content both ways.
     */
    @Query("""
            SELECT CASE WHEN b.blockerId = :userId THEN b.blockedId ELSE b.blockerId END
            FROM Block b
            WHERE b.blockerId = :userId OR b.blockedId = :userId
            """)
    List<UUID> findRelatedUserIds(@Param("userId") UUID userId);
}
