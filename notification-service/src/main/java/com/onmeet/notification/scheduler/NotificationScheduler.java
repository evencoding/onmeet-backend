package com.onmeet.notification.scheduler;

import com.onmeet.notification.entity.Notification;
import com.onmeet.notification.entity.NotificationRecipient;
import com.onmeet.notification.repository.NotificationRepository;
import com.onmeet.notification.service.NotificationService;
import com.onmeet.notification.service.NotificationSettingService;
import com.onmeet.notification.type.NotificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 10분마다 실행되어 scheduled_at이 지난 PENDING 알림을 찾아 발송합니다.
 * - 500건씩 페이징 처리 (메모리 보호)
 * - 각 알림은 개별 트랜잭션으로 처리 (1건 실패가 다른 건에 영향 없음)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final NotificationSettingService settingService;

    private static final int BATCH_SIZE = 500;

    @Scheduled(fixedRate = 600_000) // 10분 = 600,000ms
    public void processScheduledNotifications() {
        LocalDateTime now = LocalDateTime.now();

        while (true) {
            List<Notification> batch = notificationRepository.findScheduledNotifications(
                    NotificationStatus.PENDING, now, PageRequest.of(0, BATCH_SIZE));

            if (batch.isEmpty()) {
                break;
            }

            log.info("Processing batch of {} scheduled notifications", batch.size());

            for (Notification notification : batch) {
                processOneNotification(notification);
            }
        }
    }

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
                notificationService.sendFcmPush(recipient.getUserId(), notification);
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
