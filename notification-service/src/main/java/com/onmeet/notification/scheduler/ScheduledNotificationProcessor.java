package com.onmeet.notification.scheduler;

import com.onmeet.notification.entity.Notification;
import com.onmeet.notification.entity.NotificationRecipient;
import com.onmeet.notification.repository.NotificationRepository;
import com.onmeet.notification.service.NotificationService;
import com.onmeet.notification.service.NotificationSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스케줄러에서 호출되는 개별 알림 처리 로직.
 * 별도 서비스로 분리하여 @Transactional 프록시가 정상 작동하도록 합니다.
 * (동일 클래스 내 self-invocation 시 @Transactional이 적용되지 않는 문제 방지)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledNotificationProcessor {

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final NotificationSettingService settingService;

    /**
     * 개별 알림을 독립 트랜잭션으로 처리합니다.
     * 한 건이 실패해도 나머지에 영향을 주지 않습니다.
     */
    @Transactional
    public void processOneNotification(Notification notification) {
        try {
            boolean allSent = true;

            for (NotificationRecipient recipient : notification.getRecipients()) {
                // 알림 설정 검증
                if (!settingService.shouldSendNotification(recipient.getUserId(), notification.getType())) {
                    log.info("Notification blocked by settings: userId={}, type={}",
                            recipient.getUserId(), notification.getType());
                    continue;
                }

                boolean success = notificationService.sendToClient(recipient.getUserId(), notification);
                if (success) {
                    recipient.markAsSent();
                } else {
                    allSent = false;
                    log.warn("SSE not connected for user: {}, notification will remain for retry",
                            recipient.getUserId());
                }

                // FCM 푸시도 함께 전송
                try {
                    notificationService.sendFcmPush(recipient.getUserId(), notification);
                } catch (Exception e) {
                    log.warn("FCM push failed for scheduled notification: userId={}, error={}",
                            recipient.getUserId(), e.getMessage());
                }
            }

            if (allSent) {
                notification.markAsSent();
            } else {
                notification.markAsFailed();
            }

            notificationRepository.save(notification);

        } catch (Exception e) {
            log.error("Failed to process notification: id={}", notification.getId(), e);
            notification.markAsFailed();
            notificationRepository.save(notification);
        }
    }
}
