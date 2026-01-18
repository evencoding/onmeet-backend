package com.onmeet.notification.sse;

import com.onmeet.notification.dto.NotificationResponse;
import org.springframework.stereotype.Component;

@Component
public class SseNotificationPublisher implements NotificationPublisher {

    private final SseEmitterManager emitterManager;

    public SseNotificationPublisher(SseEmitterManager emitterManager) {
        this.emitterManager = emitterManager;
    }

    @Override
    public void publish(String userId, NotificationResponse response) {
        emitterManager.send(userId, response);
    }
}
