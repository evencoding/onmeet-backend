package com.onmeet.notification.sse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationSseController {

    private final SseEmitterManager emitterManager;

    public NotificationSseController(SseEmitterManager emitterManager) {
        this.emitterManager = emitterManager;
    }

    @GetMapping("/stream")
    public SseEmitter stream(@RequestHeader("X-User-Id") String userId) {
        return emitterManager.add(userId);
    }
}
