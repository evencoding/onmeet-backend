package com.onmeet.notification.dto;

import com.onmeet.notification.entity.NotificationResourceType;
import com.onmeet.notification.entity.NotificationType;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    Long id,
    NotificationType type,
    NotificationResourceType resourceType,
    UUID resourceId,
    String title,
    String body,
    Instant createdAt
) {
}
