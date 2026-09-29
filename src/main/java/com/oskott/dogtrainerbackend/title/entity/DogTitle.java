package com.oskott.dogtrainerbackend.title.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "dog_titles")
public class DogTitle {

    @Id
    private UUID id;

    @Column(name = "dog_id", nullable = false)
    private UUID dogId;

    @Column(nullable = false)
    private String title;

    @Column(name = "date_earned")
    private LocalDate dateEarned;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DogTitle() {
        // JPA
    }

    public DogTitle(UUID id, UUID dogId, String title, LocalDate dateEarned, Instant createdAt) {
        this.id = id;
        this.dogId = dogId;
        this.title = title;
        this.dateEarned = dateEarned;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDogId() {
        return dogId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDate getDateEarned() {
        return dateEarned;
    }

    public void setDateEarned(LocalDate dateEarned) {
        this.dateEarned = dateEarned;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
