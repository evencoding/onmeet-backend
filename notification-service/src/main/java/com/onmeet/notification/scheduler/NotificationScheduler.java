package com.onmeet.notification.scheduler;

import com.onmeet.notification.entity.Notification;
import com.onmeet.notification.repository.NotificationRepository;
import com.onmeet.notification.type.NotificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
    private final ScheduledNotificationProcessor processor;

    private static final int BATCH_SIZE = 500;

    @Scheduled(fixedDelay = 600_000) // 10분 = 600,000ms (작업 완료 후 기준)
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
                processor.processOneNotification(notification);
            }
        }
    }
}
