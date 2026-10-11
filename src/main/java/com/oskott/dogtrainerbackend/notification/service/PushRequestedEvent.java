package com.oskott.dogtrainerbackend.notification.service;

import java.util.List;
import java.util.UUID;

/** Published when notifications were persisted and their push delivery should start (after commit). */
public record PushRequestedEvent(List<UUID> notificationIds) {
}