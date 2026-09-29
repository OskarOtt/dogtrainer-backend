package com.oskott.dogtrainerbackend.activity.entity;

/**
 * Lifecycle status of a {@link PhysicalActivity}. Unlike {@code SessionStatus}, this also has
 * a PAUSED state: pausing freezes the elapsed timer (see {@code PhysicalActivity#pausedAt}),
 * resuming picks it back up.
 */
public enum ActivityStatus {
    IN_PROGRESS,
    PAUSED,
    COMPLETED,
    CANCELLED
}
