package com.onmeet.notification.sse;

import com.onmeet.notification.dto.NotificationResponse;

public interface NotificationPublisher {
    void publish(String userId, NotificationResponse response);
}
