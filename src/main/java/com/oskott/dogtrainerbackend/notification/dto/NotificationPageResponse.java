package com.oskott.dogtrainerbackend.notification.dto;

import java.util.List;

public record NotificationPageResponse(List<NotificationResponse> items, String nextCursor) {
}