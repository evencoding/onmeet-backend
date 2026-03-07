package com.onmeet.video.infra.notification;

public interface NotificationServiceClient {

    void sendNotification(Long userId, String type, String title, String body,
            String deeplink, Long actorUserId,
            String resourceType, String resourceId);
}
