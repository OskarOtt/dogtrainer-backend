package com.oskott.dogtrainerbackend.notification.dto;

import com.oskott.dogtrainerbackend.notification.entity.Notification;
import com.oskott.dogtrainerbackend.notification.entity.NotificationType;
import com.oskott.dogtrainerbackend.user.entity.User;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String body,
        UUID referenceId,
        boolean read,
        Instant createdAt,
        Actor actor
) {

    public record Actor(UUID id, String name, String avatarUrl) {
    }

    public static NotificationResponse from(Notification notification, User actor) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getReferenceId(),
                notification.isRead(),
                notification.getCreatedAt(),
                actor != null ? new Actor(actor.getId(), actor.getName(), actor.getAvatarUrl()) : null
        );
    }
}