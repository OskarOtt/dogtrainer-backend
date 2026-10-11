package com.oskott.dogtrainerbackend.notification.repository;

import com.oskott.dogtrainerbackend.notification.entity.PushTicket;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PushTicketRepository extends JpaRepository<PushTicket, UUID> {

    /**
     * Claims tickets old enough for a receipt using {@code FOR UPDATE SKIP LOCKED} (lock timeout
     * -2), so concurrent backend replicas never process the same ticket. Must run inside a
     * transaction that stays open until the claimed rows are deleted.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<PushTicket> findByCreatedAtBeforeOrderByCreatedAtAsc(Instant createdBefore, Limit limit);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PushTicket t WHERE t.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
