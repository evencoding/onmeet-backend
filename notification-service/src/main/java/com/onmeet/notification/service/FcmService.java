package com.onmeet.notification.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.onmeet.notification.dto.FcmTokenRequestDto;
import com.onmeet.notification.entity.FcmToken;
import com.onmeet.notification.repository.FcmTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmService {

    private final FcmTokenRepository fcmTokenRepository;
    private final FcmPushRetryService fcmPushRetryService;

    // ──────────────────────────────────────────────
    // Token Management
    // ──────────────────────────────────────────────

    @Transactional
    public void registerToken(Long userId, FcmTokenRequestDto dto) {
        // deviceId를 기반으로 기존 등록된 기기인지 확인 (Upsert 방식)
        fcmTokenRepository.findByUserIdAndDeviceId(userId, dto.getDeviceId())
                .ifPresentOrElse(
                        existingToken -> {
                            // 이미 존재하는 기기라면 토큰만 업데이트
                            if (!existingToken.getToken().equals(dto.getToken())) {
                                existingToken.updateToken(dto.getToken());
                                log.info("FCM token updated: userId={}, deviceId={}", userId, dto.getDeviceId());
                            } else {
                                log.info("FCM token already up-to-date: userId={}, deviceId={}", userId,
                                        dto.getDeviceId());
                            }
                        },
                        () -> {
                            // 새로운 기기라면 새로 등록
                            FcmToken fcmToken = FcmToken.builder()
                                    .userId(userId)
                                    .token(dto.getToken())
                                    .deviceId(dto.getDeviceId())
                                    .deviceType(dto.getDeviceType())
                                    .build();

                            fcmTokenRepository.save(fcmToken);
                            log.info("FCM token registered: userId={}, deviceId={}, deviceType={}",
                                    userId, dto.getDeviceId(), dto.getDeviceType());
                        });
    }

    @Transactional
    public void unregisterToken(Long userId, String token) {
        fcmTokenRepository.deleteByUserIdAndToken(userId, token);
        log.info("FCM token unregistered: userId={}", userId);
    }

    /**
     * 해당 유저의 모든 디바이스 토큰 목록을 조회합니다.
     */
    public List<String> getTokensByUserId(Long userId) {
        return fcmTokenRepository.findByUserId(userId).stream()
                .map(FcmToken::getToken)
                .toList();
    }

    // ──────────────────────────────────────────────
    // Push Notification
    // ──────────────────────────────────────────────

    /**
     * 특정 디바이스 토큰으로 푸시 알림을 전송합니다.
     */
    public void sendPushToToken(String token, String title, String body, String deeplink) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.debug("Firebase not initialized, skipping FCM push for token");
            return;
        }

        try {
            String response = fcmPushRetryService.sendToDevice(token, title, body, deeplink);
            if (response != null) {
                log.info("FCM push sent to token: messageId={}", response);
            }
        } catch (FirebaseMessagingException e) {
            log.error("FCM push failed for token, error={}", e.getMessage());
            // 유효하지 않은 토큰일 때의 추가 처리는 필요 시 구현 (여기서는 개별 발송이므로 로깅 후 종료)
        }
    }

    /**
     * 해당 유저의 로컬 DB에 저장된 모든 디바이스에 푸시 알림을 전송합니다.
     */
    public void sendPushToLocalTokens(Long userId, String title, String body, String deeplink) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.debug("Firebase not initialized, skipping FCM push for userId={}", userId);
            return;
        }

        List<FcmToken> tokens = fcmTokenRepository.findByUserId(userId);
        if (tokens.isEmpty()) {
            log.debug("No FCM tokens found for userId={}", userId);
            return;
        }

        for (FcmToken fcmToken : tokens) {
            try {
                String response = fcmPushRetryService.sendToDevice(
                        fcmToken.getToken(), title, body, deeplink);
                if (response != null) {
                    log.info("FCM push sent: userId={}, messageId={}", userId, response);
                }
            } catch (FirebaseMessagingException e) {
                log.error("FCM push failed for userId={}, error={}",
                        userId, e.getMessage());

                // 유효하지 않은 토큰이면 삭제
                if ("UNREGISTERED".equals(e.getMessagingErrorCode().name()) ||
                        "INVALID_ARGUMENT".equals(e.getMessagingErrorCode().name())) {
                    fcmTokenRepository.delete(fcmToken);
                    log.info("Removed invalid FCM token: userId={}", userId);
                }
            }
        }
    }
}
