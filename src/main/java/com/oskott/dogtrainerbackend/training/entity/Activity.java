package com.oskott.dogtrainerbackend.training.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "activities")
public class Activity {

    @Id
    private UUID id;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected Activity() {
        // JPA
    }

    public Activity(UUID id, UUID categoryId, String name, String description, int displayOrder) {
        this.id = id;
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.displayOrder = displayOrder;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCategoryId() {
        return categoryId;
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
