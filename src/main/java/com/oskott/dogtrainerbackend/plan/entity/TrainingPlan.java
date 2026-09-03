package com.oskott.dogtrainerbackend.plan.entity;

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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "training_plans")
public class TrainingPlan {

    @Id
    private UUID id;

    @Column(name = "dog_id", nullable = false)
    private UUID dogId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "training_plan_exercises", joinColumns = @JoinColumn(name = "plan_id"))
    @OrderColumn(name = "list_index")
    @Column(name = "exercise_id", nullable = false)
    private List<UUID> exerciseIds = new ArrayList<>();

    protected TrainingPlan() {
        // JPA
    }

    public TrainingPlan(
            UUID id,
            UUID dogId,
            String name,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            PlanStatus status,
            List<UUID> exerciseIds
    ) {
        this.id = id;
        this.dogId = dogId;
        this.name = name;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.exerciseIds = exerciseIds != null ? new ArrayList<>(exerciseIds) : new ArrayList<>();
    }

    public UUID getId() {
        return id;
    }

    public UUID getDogId() {
        return dogId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public void setStatus(PlanStatus status) {
        this.status = status;
    }

    public List<UUID> getExerciseIds() {
        return exerciseIds;
    }

    public void setExerciseIds(List<UUID> exerciseIds) {
        this.exerciseIds.clear();
        if (exerciseIds != null) {
            this.exerciseIds.addAll(exerciseIds);
        }
    }
}
