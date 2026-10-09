package com.oskott.dogtrainerbackend.activity.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A standalone, timed physical activity (walk, run, ski, etc.) logged against a dog - not
 * connected to a {@code TrainingSession}. Deliberately kept simple/self-contained (own table,
 * own service) so a future map/route/distance integration can be added here later without
 * touching training code or requiring a schema redesign.
 */
@Entity
@Table(name = "physical_activities")
public class PhysicalActivity {

    @Id
    private UUID id;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "physical_activity_dogs", joinColumns = @JoinColumn(name = "activity_id"))
    @OrderColumn(name = "list_index")
    @Column(name = "dog_id", nullable = false)
    private List<UUID> dogIds = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false)
    private ActivityType activityType;

    @Column(nullable = false)
    private String title;

    private String notes;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "paused_at")
    private Instant pausedAt;

    @Column(name = "total_paused_seconds", nullable = false)
    private long totalPausedSeconds;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityStatus status;

    protected PhysicalActivity() {
        // JPA
    }

    public PhysicalActivity(UUID id, List<UUID> dogIds, ActivityType activityType, String title, Instant startedAt, ActivityStatus status) {
        this.id = id;
        this.dogIds = new ArrayList<>(dogIds);
        this.activityType = activityType;
        this.title = title;
        this.startedAt = startedAt;
        this.totalPausedSeconds = 0;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public List<UUID> getDogIds() {
        return dogIds;
    }

    public ActivityType getActivityType() {
        return activityType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getPausedAt() {
        return pausedAt;
    }

    public void setPausedAt(Instant pausedAt) {
        this.pausedAt = pausedAt;
    }

    public long getTotalPausedSeconds() {
        return totalPausedSeconds;
    }

    public void setTotalPausedSeconds(long totalPausedSeconds) {
        this.totalPausedSeconds = totalPausedSeconds;
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

    public ActivityStatus getStatus() {
        return status;
    }

    public void setStatus(ActivityStatus status) {
        this.status = status;
    }
}
