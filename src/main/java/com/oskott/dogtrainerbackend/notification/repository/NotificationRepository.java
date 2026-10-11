package com.oskott.dogtrainerbackend.notification.repository;

import com.oskott.dogtrainerbackend.notification.entity.Notification;
import com.oskott.dogtrainerbackend.notification.entity.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);

    boolean existsByRecipientIdAndDedupeKey(UUID recipientId, String dedupeKey);

    @Query("SELECT n.recipientId FROM Notification n WHERE n.dedupeKey = :dedupeKey AND n.recipientId IN :recipientIds")
    List<UUID> findRecipientIdsWithDedupeKey(
            @Param("dedupeKey") String dedupeKey,
            @Param("recipientIds") Collection<UUID> recipientIds
    );

    long countByRecipientIdAndReadFalse(UUID recipientId);

    /** First page - split from {@link #findPageAfterCursor} to avoid null-typed parameters on Postgres. */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.recipientId = :recipientId
            ORDER BY n.createdAt DESC, n.id DESC
            """)
    List<Notification> findFirstPage(@Param("recipientId") UUID recipientId, Pageable pageable);

    @Query("""
            SELECT n FROM Notification n
            WHERE n.recipientId = :recipientId
            AND (n.createdAt < :cursorCreatedAt
                 OR (n.createdAt = :cursorCreatedAt AND n.id < :cursorId))
            ORDER BY n.createdAt DESC, n.id DESC
            """)
    List<Notification> findPageAfterCursor(
            @Param("recipientId") UUID recipientId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );

    default List<Notification> findPage(UUID recipientId, Instant cursorCreatedAt, UUID cursorId, Pageable pageable) {
        return cursorCreatedAt == null
                ? findFirstPage(recipientId, pageable)
                : findPageAfterCursor(recipientId, cursorCreatedAt, cursorId, pageable);
    }

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.read = true WHERE n.recipientId = :recipientId AND n.read = false")
    int markAllRead(@Param("recipientId") UUID recipientId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.recipientId = :userId OR n.actorId = :userId")
    void deleteAllInvolving(@Param("userId") UUID userId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.referenceId = :referenceId AND n.type IN :types")
    void deleteByReferenceIdAndTypeIn(
            @Param("referenceId") UUID referenceId,
            @Param("types") Collection<NotificationType> types
    );
}
