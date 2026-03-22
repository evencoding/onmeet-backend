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
    private java.util.List<Long> userIds;
    private String type; // String으로 받고 내부에서 처리
    private String title;
    private String body;
    private String deeplink;
    private java.time.LocalDateTime scheduledAt;
    private String resourceType; // String으로 받고 내부에서 처리
    private String dedupeKey;
    private String resourceId;
    private Long actorUserId;
}
