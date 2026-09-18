package com.oskott.dogtrainerbackend.moderation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's report of a post and/or another user, for manual review. There is no moderation UI
 * yet - reports are just persisted for now.
 */
@Entity
@Table(name = "reports")
public class Report {

    @Id
    private UUID id;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "post_id")
    private UUID postId;

    @Column(name = "reported_user_id")
    private UUID reportedUserId;

    @Column(nullable = false, length = 50)
    private String reason;

    @Column(length = 1024)
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Report() {
        // JPA
    }

    public Report(UUID id, UUID reporterId, UUID postId, UUID reportedUserId, String reason, String details, Instant createdAt) {
        this.id = id;
        this.reporterId = reporterId;
        this.postId = postId;
        this.reportedUserId = reportedUserId;
        this.reason = reason;
        this.details = details;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReporterId() {
        return reporterId;
    }

    public UUID getPostId() {
        return postId;
    }

    public UUID getReportedUserId() {
        return reportedUserId;
    }

    public String getReason() {
        return reason;
    }

    public String getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
