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
@Table(name = "user_devices")
public class UserDevice {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "expo_push_token", nullable = false, unique = true)
    private String expoPushToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DevicePlatform platform;

    @Column(nullable = false)
    private String locale;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected UserDevice() {
        // JPA
    }

    public UserDevice(UUID id, UUID userId, String expoPushToken, DevicePlatform platform, String locale, Instant now) {
        this.id = id;
        this.userId = userId;
        this.expoPushToken = expoPushToken;
        this.platform = platform;
        this.locale = locale;
        this.createdAt = now;
        this.lastSeenAt = now;
    }

    /** Re-registration of a known token: it may now belong to a different user (shared device). */
    public void reassign(UUID userId, DevicePlatform platform, String locale, Instant now) {
        this.userId = userId;
        this.platform = platform;
        this.locale = locale;
        this.lastSeenAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getExpoPushToken() {
        return expoPushToken;
    }

    public DevicePlatform getPlatform() {
        return platform;
    }

    public String getLocale() {
        return locale;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
