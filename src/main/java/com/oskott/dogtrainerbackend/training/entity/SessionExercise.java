package com.oskott.dogtrainerbackend.training.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "session_exercises")
public class SessionExercise {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(nullable = false)
    private int repetitions;

    @Column(name = "successful_repetitions", nullable = false)
    private int successfulRepetitions;

    @Enumerated(EnumType.STRING)
    private Difficulty difficulty;

    private String notes;

    protected SessionExercise() {
        // JPA
    }

    public SessionExercise(
            UUID id,
            UUID sessionId,
            UUID exerciseId,
            int repetitions,
            int successfulRepetitions,
            Difficulty difficulty,
            String notes
    ) {
        this.id = id;
        this.sessionId = sessionId;
        this.exerciseId = exerciseId;
        this.repetitions = repetitions;
        this.successfulRepetitions = successfulRepetitions;
        this.difficulty = difficulty;
        this.notes = notes;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }

    public int getRepetitions() {
        return repetitions;
    }

    public void setRepetitions(int repetitions) {
        this.repetitions = repetitions;
    }

    public int getSuccessfulRepetitions() {
        return successfulRepetitions;
    }

    public void setSuccessfulRepetitions(int successfulRepetitions) {
        this.successfulRepetitions = successfulRepetitions;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    /**
     * Success rate as a value between 0 and 1, derived from repetitions and successful
     * repetitions. Returns 0 when no repetitions have been recorded yet.
     */
    public double successRate() {
        return repetitions == 0 ? 0.0 : (double) successfulRepetitions / repetitions;
    }
}
