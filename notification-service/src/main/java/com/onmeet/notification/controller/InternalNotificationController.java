package com.onmeet.notification.controller;

import com.onmeet.notification.dto.NotificationRequestDto;
import com.onmeet.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 내부 서비스 간 알림 발송용 API
 * Gateway의 X-Gateway-Secret 헤더로 인증됩니다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/notification/internal")
@Tag(name = "Internal Notification", description = "내부 서비스 간 알림 발송 API")
public class InternalNotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "알림 발송", description = "타 서비스에서 호출하여 특정 사용자에게 알림을 발송합니다.")
    @PostMapping("/send")
    public ResponseEntity<Void> sendNotification(@RequestBody NotificationRequestDto dto) {
        log.info("Internal notification request: userId={}, type={}", dto.getUserId(), dto.getType());
        notificationService.send(dto);
        return ResponseEntity.ok().build();
    }
}
