package com.oskott.dogtrainerbackend.post.repository;

import com.oskott.dogtrainerbackend.post.entity.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    boolean existsByTrainingSessionId(UUID trainingSessionId);

    /**
     * Keyset (cursor) pagination: pass {@code cursorCreatedAt}/{@code cursorId} as null for the
     * first page, then the last returned post's createdAt/id for subsequent pages. This stays
     * correct even as new posts are inserted while a caller is paging through, unlike offset
     * pagination. {@code pageable} is only ever page 0 - it exists purely to cap the result size.
     */
    @Query("""
            SELECT p FROM Post p
            WHERE p.authorId IN :authorIds
            AND (:cursorCreatedAt IS NULL
                 OR p.createdAt < :cursorCreatedAt
                 OR (p.createdAt = :cursorCreatedAt AND p.id < :cursorId))
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<Post> findPage(
            @Param("authorIds") List<UUID> authorIds,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );
}
