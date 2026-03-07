package com.onmeet.notification.scheduler;

import com.onmeet.notification.repository.NotificationRecipientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 매일 새벽 3시에 오래된 알림 수신 기록을 자동으로 정리합니다.
 * <p>
 * - 보존 기간: 15일
 * - 배치 크기: 1000건씩 반복 삭제 (메모리 보호)
 * - 기존 NotificationRecipientRepository.deleteOlderThan() 쿼리 활용
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationCleanupScheduler {

    private final NotificationRecipientRepository recipientRepository;

    private static final int RETENTION_DAYS = 15;
    private static final int BATCH_SIZE = 1000;

    @Scheduled(cron = "0 0 3 * * *") // 매일 새벽 3시
    @Transactional
    public void cleanupOldNotifications() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int totalDeleted = 0;

        log.info("Starting notification cleanup: deleting records older than {} days (before {})",
                RETENTION_DAYS, cutoff);

        while (true) {
            int deleted = recipientRepository.deleteOlderThan(cutoff, BATCH_SIZE);
            totalDeleted += deleted;

            if (deleted < BATCH_SIZE) {
                break; // 마지막 배치이거나 삭제할 데이터가 없음
            }
        }

        if (totalDeleted > 0) {
            log.info("Notification cleanup completed: {} old records deleted (older than {} days)",
                    totalDeleted, RETENTION_DAYS);
        } else {
            log.debug("Notification cleanup: no old records to delete");
        }
    }
}
