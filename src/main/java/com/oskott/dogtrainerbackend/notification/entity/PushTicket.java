package com.oskott.dogtrainerbackend.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * An accepted Expo push ticket waiting for its delivery receipt. Rows only exist until the
 * receipt has been processed.
 */
@Entity
@Table(name = "push_tickets")
public class PushTicket {

    @Id
    private UUID id;

    @Column(name = "expo_ticket_id", nullable = false, updatable = false)
    private String expoTicketId;

    @Column(name = "expo_push_token", nullable = false, updatable = false)
    private String expoPushToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PushTicket() {
        // JPA
    }

    public PushTicket(UUID id, String expoTicketId, String expoPushToken, Instant createdAt) {
        this.id = id;
        this.expoTicketId = expoTicketId;
        this.expoPushToken = expoPushToken;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getExpoTicketId() {
        return expoTicketId;
    }

    public String getExpoPushToken() {
        return expoPushToken;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
