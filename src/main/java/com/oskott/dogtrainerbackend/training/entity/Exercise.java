package com.oskott.dogtrainerbackend.training.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "exercises")
public class Exercise {

    @Id
    private UUID id;

    @Column(name = "activity_id", nullable = false)
    private UUID activityId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    private String instructions;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected Exercise() {
        // JPA
    }

    public Exercise(
            UUID id,
            UUID activityId,
            String name,
            String description,
            Difficulty difficulty,
            String instructions,
            int displayOrder
    ) {
        this.id = id;
        this.activityId = activityId;
        this.name = name;
        this.description = description;
        this.difficulty = difficulty;
        this.instructions = instructions;
        this.displayOrder = displayOrder;
    }

    public UUID getId() {
        return id;
    }

    public UUID getActivityId() {
        return activityId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public String getInstructions() {
        return instructions;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
