package com.onmeet.notification.service;

import com.onmeet.notification.dto.NotificationRequestDto;
import com.onmeet.notification.dto.NotificationResponseDto;
import com.onmeet.notification.entity.Notification;
import com.onmeet.notification.entity.NotificationRecipient;
import com.onmeet.notification.entity.NotificationStream;
import com.onmeet.notification.repository.NotificationRepository;
import com.onmeet.notification.repository.NotificationStreamRepository;
import com.onmeet.notification.type.NotificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationStreamRepository streamRepository;
    private final NotificationSettingService settingService;
    private final FcmService fcmService;

    // In-memory storage for active emitters (for real-time push)
    // Structure: Map<UserId, Map<EmitterId, SseEmitter>>
    // This allows multiple connections per user (multi-tab/device support)
    private final Map<Long, Map<String, SseEmitter>> emitters = new ConcurrentHashMap<>();

    private static final Long DEFAULT_TIMEOUT = 60L * 1000 * 60; // 60 minutes
    private static final long HEARTBEAT_INTERVAL = 30_000L; // 30 seconds

    // ──────────────────────────────────────────────
    // SSE Subscribe
    // ──────────────────────────────────────────────

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        // Unique ID for this connection: userId + UUID (collision-safe)
        String emitterId = userId + "_" + java.util.UUID.randomUUID();

        saveNotificationStream(userId, emitterId);

        emitter.onCompletion(() -> removeEmitter(userId, emitterId));
        emitter.onTimeout(() -> removeEmitter(userId, emitterId));
        emitter.onError((e) -> removeEmitter(userId, emitterId));

        // Add to nested map
        emitters.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).put(emitterId, emitter);

        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("Connected! Emitter ID: " + emitterId));
        } catch (IOException e) {
            log.error("Failed to send initial SSE event to user: {}", userId, e);
            removeEmitter(userId, emitterId);
        }

        return emitter;
    }

    private void removeEmitter(Long userId, String emitterId) {
        Map<String, SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters != null) {
            userEmitters.remove(emitterId);
            if (userEmitters.isEmpty()) {
                emitters.remove(userId);
            }
        }
        removeNotificationStream(emitterId);
        log.debug("SSE connection removed: userId={}, emitterId={}", userId, emitterId);
    }

    // ──────────────────────────────────────────────
    // 알림 전송 (즉시 / 예약)
    // ──────────────────────────────────────────────

    @Transactional
    public void send(NotificationRequestDto dto) {
        boolean isScheduled = dto.getScheduledAt() != null;

        Notification notification = Notification.builder()
                .type(dto.getType())
                .title(dto.getTitle())
                .body(dto.getBody())
                .deeplink(dto.getDeeplink())
                .scheduledAt(dto.getScheduledAt())
                .resourceType(dto.getResourceType())
                .dedupeKey(dto.getDedupeKey())
                .resourceId(dto.getResourceId())
                .actorUserId(dto.getActorUserId())
                .status(isScheduled ? NotificationStatus.PENDING : NotificationStatus.SENT)
                .build();

        NotificationRecipient recipient = NotificationRecipient.builder()
                .userId(dto.getUserId())
                .build();

        notification.addRecipient(recipient);
        notificationRepository.save(notification);

        // 즉시 발송: scheduledAt이 없으면 바로 보냄
        if (!isScheduled) {
            // 알림 설정 검증
            if (!settingService.shouldSendNotification(dto.getUserId(), notification.getType())) {
                log.info("Notification blocked by user settings: userId={}, type={}",
                        dto.getUserId(), notification.getType());
                return;
            }

            boolean sseSent = sendToClient(dto.getUserId(), notification);
            if (sseSent) {
                recipient.markAsSent();
            }

            // FCM 푸시도 함께 전송 (SSE 성공 여부와 무관, 실패해도 API 응답에 영향 없음)
            try {
                fcmService.sendPush(dto.getUserId(), notification.getTitle(),
                        notification.getBody(), notification.getDeeplink());
            } catch (Exception e) {
                log.warn("FCM push failed but notification was saved: userId={}, error={}",
                        dto.getUserId(), e.getMessage());
            }
        }
    }

    // ──────────────────────────────────────────────
    // SSE Push (스케줄러에서도 호출)
    // ──────────────────────────────────────────────

    /**
     * 특정 유저의 *모든* 연결된 SSE Emitter로 알림을 전송합니다.
     * 하나라도 성공하면 true를 반환합니다.
     */
    public boolean sendToClient(Long userId, Notification notification) {
        Map<String, SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return false;
        }

        boolean anySuccess = false;
        for (Map.Entry<String, SseEmitter> entry : userEmitters.entrySet()) {
            String emitterId = entry.getKey();
            SseEmitter emitter = entry.getValue();
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(notification.getId()))
                        .name("notification")
                        .data(NotificationResponseDto.from(notification)));
                anySuccess = true;
            } catch (IOException e) {
                log.error("Failed to send notification to user: {}, emitterId={}", userId, emitterId, e);
                removeEmitter(userId, emitterId);
            }
        }
        return anySuccess;
    }

    // ──────────────────────────────────────────────
    // FCM Push (스케줄러에서도 호출)
    // ──────────────────────────────────────────────

    public void sendFcmPush(Long userId, Notification notification) {
        fcmService.sendPush(userId, notification.getTitle(),
                notification.getBody(), notification.getDeeplink());
    }

    // ──────────────────────────────────────────────
    // Heartbeat (Keep-Alive)
    // ──────────────────────────────────────────────

    @Scheduled(fixedRate = HEARTBEAT_INTERVAL)
    public void sendHeartbeat() {
        log.debug("Sending heartbeat to {} active users", emitters.size());
        emitters.forEach((userId, userEmitters) -> {
            userEmitters.forEach((emitterId, emitter) -> {
                try {
                    emitter.send(SseEmitter.event()
                            .comment("heartbeat")
                            .name("heartbeat")
                            .data(""));
                } catch (IOException e) {
                    // Heartbeat 실패 시 연결 끊김으로 간주하고 제거
                    log.debug("Heartbeat failed for user={}, emitterId={}", userId, emitterId);
                    removeEmitter(userId, emitterId);
                }
            });
        });
    }

    // ──────────────────────────────────────────────
    // NotificationStream 관리
    // ──────────────────────────────────────────────

    private void saveNotificationStream(Long userId, String streamId) {
        try {
            NotificationStream stream = NotificationStream.builder()
                    .id(streamId)
                    .userId(userId)
                    .clientId("web-client")
                    .connectedAt(LocalDateTime.now())
                    .lastSeenAt(LocalDateTime.now())
                    .lastEventId(null)
                    .build();
            streamRepository.save(stream);
        } catch (Exception e) {
            log.error("Failed to save notification stream for user: {}", userId, e);
        }
    }

    private void removeNotificationStream(String streamId) {
        try {
            streamRepository.deleteById(streamId);
        } catch (Exception e) {
            log.error("Failed to remove notification stream: {}", streamId, e);
        }
    }
}
