package com.onmeet.notification.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * FCM 개별 디바이스 푸시 전송 전용 서비스.
 * <p>
 * Spring Retry의 @Retryable은 AOP 프록시 기반이므로,
 * 같은 클래스 내 self-invocation에서는 동작하지 않습니다.
 * 따라서 FcmService에서 이 서비스를 DI 받아 호출하는 구조로 설계합니다.
 * <p>
 * 재시도 정책: 최대 3회, 1초 → 2초 → 4초 (exponential backoff)
 */
@Service
@Slf4j
public class FcmPushRetryService {

    /**
     * 단일 FCM 토큰에 푸시를 전송합니다.
     * FirebaseMessagingException 발생 시 최대 3회 자동 재시도합니다.
     *
     * @return Firebase 응답 메시지 ID (성공 시), null (최종 실패 시)
     */
    @Retryable(retryFor = FirebaseMessagingException.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String sendToDevice(String token, String title, String body, String deeplink)
            throws FirebaseMessagingException {
        Message message = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putData("deeplink", deeplink != null ? deeplink : "")
                .build();

        String response = FirebaseMessaging.getInstance().send(message);
        log.debug("FCM push sent: token={}, messageId={}", token, response);
        return response;
    }

    /**
     * 3회 재시도 후에도 실패한 경우 호출됩니다.
     * 로그만 남기고 정상 리턴하여 전체 알림 발송 플로우가 중단되지 않도록 합니다.
     */
    @Recover
    public String recover(FirebaseMessagingException e, String token, String title, String body, String deeplink) {
        log.error("FCM push 최종 실패 (3회 재시도 후): token={}, error={}", token, e.getMessage());
        return null;
    }
}
