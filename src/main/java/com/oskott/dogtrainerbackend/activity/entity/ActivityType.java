package com.oskott.dogtrainerbackend.activity.entity;

/**
 * Kinds of physical activity a dog owner can log outside of a structured training session
 * (no exercises/repetitions - just a timed activity). Not connected to {@code TrainingSession}.
 */
public enum ActivityType {
    WALK,
    RUN,
    SKI,
    STRENGTH_TRAINING,
    SWIM,
    HIKE,
    PLAY_SESSION
}
