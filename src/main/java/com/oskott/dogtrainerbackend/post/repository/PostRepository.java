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
     * Keyset (cursor) pagination, first page: no cursor params at all, so Postgres never has to
     * infer a type for a null-valued parameter. Split out from {@link #findPageAfterCursor} to
     * avoid HHH000247 / SQLState 42P18 ("could not determine data type of parameter") that occurs
     * when {@code cursorCreatedAt}/{@code cursorId} are bound as null. This stays correct even as
     * new posts are inserted while a caller is paging through, unlike offset pagination.
     * {@code pageable} is only ever page 0 - it exists purely to cap the result size.
     */
    @Query("""
            SELECT p FROM Post p
            WHERE p.authorId IN :authorIds
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<Post> findFirstPage(
            @Param("authorIds") List<UUID> authorIds,
            Pageable pageable
    );

    /**
     * Keyset (cursor) pagination, subsequent pages: pass the last returned post's createdAt/id
     * from the previous page. See {@link #findFirstPage} for why the first page is a separate
     * query.
     */
    @Query("""
            SELECT p FROM Post p
            WHERE p.authorId IN :authorIds
            AND (p.createdAt < :cursorCreatedAt
                 OR (p.createdAt = :cursorCreatedAt AND p.id < :cursorId))
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<Post> findPageAfterCursor(
            @Param("authorIds") List<UUID> authorIds,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );

    default List<Post> findPage(List<UUID> authorIds, Instant cursorCreatedAt, UUID cursorId, Pageable pageable) {
        return cursorCreatedAt == null
                ? findFirstPage(authorIds, pageable)
                : findPageAfterCursor(authorIds, cursorCreatedAt, cursorId, pageable);
    }
}
