package com.oskott.dogtrainerbackend.auth.entity;

import com.oskott.dogtrainerbackend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_identities")
public class ExternalIdentity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExternalAuthProvider provider;

    @Column(name = "provider_subject", nullable = false)
    private String providerSubject;

    @Column(name = "provider_email")
    private String providerEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ExternalIdentity() {
        // JPA
    }

    public ExternalIdentity(
            UUID id,
            User user,
            ExternalAuthProvider provider,
            String providerSubject,
            String providerEmail,
            Instant createdAt
    ) {
        this.id = id;
        this.user = user;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.providerEmail = providerEmail;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public ExternalAuthProvider getProvider() {
        return provider;
    }

    public String getProviderSubject() {
        return providerSubject;
    }

    public String getProviderEmail() {
        return providerEmail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateProviderEmail(String providerEmail, Instant updatedAt) {
        if (providerEmail != null) {
            this.providerEmail = providerEmail;
        }
        this.updatedAt = updatedAt;
    }
}
