package com.onmeet.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.onmeet.notification.type.NotificationType;
import com.onmeet.notification.type.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationRequestDto {
    private Long userId;
    private NotificationType type;
    private String title;
    private String body;
    private String deeplink;
    private LocalDateTime scheduledAt;
    private ResourceType resourceType;
    private String dedupeKey;
    private String resourceId;
    private Long actorUserId;
}
