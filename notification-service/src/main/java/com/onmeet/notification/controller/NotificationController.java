package com.onmeet.notification.controller;

import com.onmeet.notification.dto.NotificationRequestDto;
import com.onmeet.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/notification/v1")
@Tag(name = "Notification", description = "알림 API")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "알림 서비스 상태/정보 조회", description = "알림 서비스의 상태 및 내 정보를 조회합니다.")
    @GetMapping("/me")
    public String me(@AuthenticationPrincipal String userId) {
        return "Hello from Notification Service! User ID: " + (userId != null ? userId : "Unknown");
    }

    @Operation(summary = "SSE 구독", description = "클라이언트가 알림을 수신하기 위해 SSE 연결을 구독합니다.")
    @GetMapping(value = "/subscribe/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable Long userId) {
        return notificationService.subscribe(userId);
    }

    @Operation(summary = "알림 전송 (테스트용)", description = "특정 사용자에게 알림을 전송합니다.")
    @PostMapping("/send")
    public ResponseEntity<Void> sendNotification(@RequestBody NotificationRequestDto request) {
        notificationService.send(request);
        return ResponseEntity.ok().build();
    }
}
