package com.onmeet.notification.controller;

import com.onmeet.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/notification/v1/sse")
@Tag(name = "SSE", description = "실시간 알림 SSE 구독 API")
public class SseController {

    private final NotificationService notificationService;

    @Operation(summary = "SSE 알림 구독", description = "현재 인증된 사용자의 실시간 알림 스트림에 구독합니다. "
            + "로그인 후 프론트엔드에서 이 엔드포인트를 호출하여 SSE 연결을 유지해야 합니다. "
            + "멀티탭/멀티디바이스 지원 — 동일 사용자의 다중 연결이 허용됩니다.")
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestHeader("X-User-Id") Long userId) {
        log.info("SSE subscribe request: userId={}", userId);
        return notificationService.subscribe(userId);
    }
}
