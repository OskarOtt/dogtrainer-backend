package com.oskott.dogtrainerbackend.moderation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Records that {@code blockerId} has blocked {@code blockedId}. Blocking is one-directional to
 * record ("who blocked whom"), but its effects (hidden content, no following) are applied both
 * ways - see {@code ModerationService#isBlockedEitherWay}.
 */
@Entity
@Table(name = "blocks")
public class Block {

    @Id
    private UUID id;

    @Column(name = "blocker_id", nullable = false)
    private UUID blockerId;

    @Column(name = "blocked_id", nullable = false)
    private UUID blockedId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Block() {
        // JPA
    }

    public Block(UUID id, UUID blockerId, UUID blockedId, Instant createdAt) {
        this.id = id;
        this.blockerId = blockerId;
        this.blockedId = blockedId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBlockerId() {
        return blockerId;
    }

    public UUID getBlockedId() {
        return blockedId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
