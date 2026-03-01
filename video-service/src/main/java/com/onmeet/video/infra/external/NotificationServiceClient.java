package com.onmeet.video.infra.external;

/**
 * Notification Service와 통신하기 위한 클라이언트 인터페이스
 */
public interface NotificationServiceClient {

    /**
     * 알림 발송 요청
     *
     * @param userId       알림 수신 대상 사용자 ID
     * @param type         알림 타입 (NotificationType enum 이름)
     * @param title        알림 제목
     * @param body         알림 본문
     * @param deeplink     딥링크 (nullable)
     * @param actorUserId  알림을 발생시킨 사용자 ID (nullable)
     * @param resourceType 리소스 타입 (예: "MEETING")
     * @param resourceId   리소스 ID (예: roomId)
     */
    void sendNotification(Long userId, String type, String title, String body,
            String deeplink, Long actorUserId,
            String resourceType, String resourceId);
}
