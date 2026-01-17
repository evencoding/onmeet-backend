package com.onmeet.notification.dto;

import com.onmeet.notification.entity.NotificationResourceType;
import com.onmeet.notification.entity.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record NotificationCreateRequest(
    @NotNull NotificationType type,
    @NotNull NotificationResourceType resourceType,
    @NotNull UUID resourceId,
    @NotBlank String title,
    String body,
    @NotEmpty List<String> recipientUserIds
) {
}
