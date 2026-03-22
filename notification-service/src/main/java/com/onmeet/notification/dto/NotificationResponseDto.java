package com.onmeet.notification.dto;

import com.onmeet.notification.entity.Notification;
import com.onmeet.notification.entity.NotificationRecipient;
import com.onmeet.notification.type.NotificationType;
import com.onmeet.notification.type.ResourceType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NotificationResponseDto {
    private Long id;
    private NotificationType type;
    private String title;
    private String body;
    private String deeplink;
    private LocalDateTime createdAt;
    private LocalDateTime scheduledAt;
    private ResourceType resourceType;
    private String dedupeKey;
    private String resourceId;
    private Long actorUserId;
    private boolean read;

    public static NotificationResponseDto from(NotificationRecipient recipient) {
        Notification notification = recipient.getNotification();
        return NotificationResponseDto.builder()
                .id(recipient.getId()) // recipient의 ID (우리가 단건 제어시 사용하는 ID)
                .type(notification.getType())
                .title(notification.getTitle())
                .body(notification.getBody())
                .deeplink(notification.getDeeplink())
                .createdAt(notification.getCreatedAt())
                .scheduledAt(notification.getScheduledAt())
                .resourceType(notification.getResourceType())
                .dedupeKey(notification.getDedupeKey())
                .resourceId(notification.getResourceId())
                .actorUserId(notification.getActorUserId())
                .read(recipient.getReadAt() != null)
                .build();
    }
}
