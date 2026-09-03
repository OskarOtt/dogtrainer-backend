package com.oskott.dogtrainerbackend.training.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "training_sessions")
public class TrainingSession {

    @Id
    private UUID id;

    @Column(name = "dog_id", nullable = false)
    private UUID dogId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    private String location;

    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    protected TrainingSession() {
        // JPA
    }

    public TrainingSession(UUID id, UUID dogId, Instant startedAt, String location, String notes, SessionStatus status) {
        this.id = id;
        this.dogId = dogId;
        this.startedAt = startedAt;
        this.location = location;
        this.notes = notes;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDogId() {
        return dogId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }
}
