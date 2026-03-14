package com.onmeet.notification.scheduler;

import com.onmeet.notification.entity.Notification;
import com.onmeet.notification.entity.NotificationRecipient;
import com.onmeet.notification.infra.AuthServiceClient;
import com.onmeet.notification.repository.NotificationRepository;
import com.onmeet.notification.service.NotificationService;
import com.onmeet.notification.service.NotificationSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    private final AuthServiceClient authServiceClient;

    /**
     * 개별 알림을 독립 트랜잭션으로 처리합니다.
     * 한 건이 실패해도 나머지에 영향을 주지 않습니다.
     */
    @Transactional
    public void processOneNotification(Notification notification) {
        try {
            boolean allSent = true;
            List<NotificationRecipient> recipients = notification.getRecipients();

            // N+1 방지: 모든 수신자의 최신 FCM 토큰을 일괄 조회
            List<Long> userIds = recipients.stream()
                    .map(NotificationRecipient::userId)
                    .collect(Collectors.toList());
            
            List<AuthServiceClient.UserInfoResponse> userInfos = authServiceClient.getBatchUserInfo(userIds);
            Map<Long, String> userTokenMap = userInfos.stream()
                    .filter(u -> u.fcmDeviceToken() != null && !u.fcmDeviceToken().isBlank())
                    .collect(Collectors.toMap(AuthServiceClient.UserInfoResponse::userId, AuthServiceClient.UserInfoResponse::fcmDeviceToken, (a, b) -> a));

            for (NotificationRecipient recipient : recipients) {
                Long userId = recipient.getUserId();

                // 알림 설정 검증
                if (!settingService.shouldSendNotification(userId, notification.getType())) {
                    log.info("Notification blocked by settings: userId={}, type={}",
                            userId, notification.getType());
                    continue;
                }

                // SSE 전송
                boolean sseSuccess = notificationService.sendToClient(userId, recipient);
                if (sseSuccess) {
                    recipient.markAsSent();
                } else {
                    allSent = false;
                    log.warn("SSE not connected for user: {}, notification will remain for retry", userId);
                }

                // FCM 푸시 전송 (최신 토큰 & 로컬 토큰 합집합으로 중복 방지)
                try {
                    String latestToken = userTokenMap.get(userId);
                    notificationService.sendFcmPushToUniqueTokens(userId, notification, latestToken);
                } catch (Exception e) {
                    log.warn("FCM push failed for scheduled notification: userId={}, error={}",
                            userId, e.getMessage());
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
