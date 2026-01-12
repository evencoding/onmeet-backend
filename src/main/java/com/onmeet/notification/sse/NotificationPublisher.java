package com.onmeet.notification.sse;

import com.onmeet.notification.dto.NotificationResponse;

public interface NotificationPublisher {
    void publish(Long userId, NotificationResponse response);
}
