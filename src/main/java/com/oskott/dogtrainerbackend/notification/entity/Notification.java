package com.oskott.dogtrainerbackend.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    private UUID id;

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private UUID recipientId;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private NotificationType type;

    @Column(nullable = false, updatable = false)
    private String title;

    @Column(nullable = false, updatable = false)
    private String body;

    /** Post id for {@code NEW_POST}/{@code COMMENT}, the follower's user id for {@code NEW_FOLLOWER}. */
    @Column(name = "reference_id", nullable = false, updatable = false)
    private UUID referenceId;

    /** Unique per recipient: guarantees one notification per underlying event. */
    @Column(name = "dedupe_key", nullable = false, updatable = false)
    private String dedupeKey;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
        // JPA
    }

    public Notification(
            UUID id,
            UUID recipientId,
            UUID actorId,
            NotificationType type,
            String title,
            String body,
            UUID referenceId,
            String dedupeKey,
            Instant createdAt
    ) {
        this.id = id;
        this.recipientId = recipientId;
        this.actorId = actorId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.referenceId = referenceId;
        this.dedupeKey = dedupeKey;
        this.read = false;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRecipientId() {
        return recipientId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public boolean isRead() {
        return read;
    }

    public void markRead() {
        this.read = true;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
