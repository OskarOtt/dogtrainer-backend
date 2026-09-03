package com.oskott.dogtrainerbackend.training.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "training_categories")
public class TrainingCategory {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected TrainingCategory() {
        // JPA
    }

    public TrainingCategory(UUID id, String name, String description, int displayOrder) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.displayOrder = displayOrder;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
