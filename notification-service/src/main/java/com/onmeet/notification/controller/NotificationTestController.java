package com.onmeet.notification.controller;

import com.onmeet.notification.dto.NotificationRequestDto;
import com.onmeet.notification.service.NotificationService;
import com.onmeet.notification.type.NotificationType;
import com.onmeet.notification.type.ResourceType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/test/notifications")
@Tag(name = "Notification Test", description = "알림 테스트 API (개발용)")
public class NotificationTestController {

    private final NotificationService notificationService;

    @Operation(
        summary = "테스트 알림 전송",
        description = "임의의 알림을 특정 사용자에게 전송합니다. SSE + FCM 모두 동작합니다."
    )
    @PostMapping("/send")
    public ResponseEntity<Map<String, String>> sendTestNotification(
            @RequestBody NotificationRequestDto request) {

        if (request.getType() == null) {
            request.setType("SYSTEM");
        }
        if (request.getResourceType() == null) {
            request.setResourceType("SYSTEM");
        }
        if (request.getResourceId() == null) {
            request.setResourceId("test-" + System.currentTimeMillis());
        }

        notificationService.send(request);

        return ResponseEntity.ok(Map.of(
                "status", "sent",
                "targetUserId", String.valueOf(request.getUserId()),
                "type", request.getType()
        ));
    }

    @Operation(
        summary = "사용 가능한 알림 타입 목록",
        description = "NotificationType enum의 전체 목록을 반환합니다."
    )
    @GetMapping("/types")
    public ResponseEntity<List<String>> getNotificationTypes() {
        return ResponseEntity.ok(
                Arrays.stream(NotificationType.values())
                        .map(Enum::name)
                        .collect(Collectors.toList())
        );
    }

    @Operation(
        summary = "사용 가능한 리소스 타입 목록",
        description = "ResourceType enum의 전체 목록을 반환합니다."
    )
    @GetMapping("/resource-types")
    public ResponseEntity<List<String>> getResourceTypes() {
        return ResponseEntity.ok(
                Arrays.stream(ResourceType.values())
                        .map(Enum::name)
                        .collect(Collectors.toList())
        );
    }
}
