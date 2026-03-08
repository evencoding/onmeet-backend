package com.onmeet.notification.service;

import com.onmeet.notification.dto.NotificationRequestDto;
import com.onmeet.notification.dto.NotificationResponseDto;
import com.onmeet.notification.entity.Notification;
import com.onmeet.notification.entity.NotificationRecipient;
import com.onmeet.notification.entity.NotificationStream;
import com.onmeet.notification.infra.AuthServiceClient;
import com.onmeet.notification.repository.NotificationRepository;
import com.onmeet.notification.repository.NotificationStreamRepository;
import com.onmeet.notification.type.NotificationStatus;
import com.onmeet.notification.type.NotificationTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
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
    private final AuthServiceClient authServiceClient;

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
        if (dto.getUserIds() != null && !dto.getUserIds().isEmpty()) {
            // 다수 수신자 처리
            for (Long userId : dto.getUserIds()) {
                NotificationRequestDto singleDto = copyForSingleUser(dto, userId);
                sendSingle(singleDto);
            }
        } else if (dto.getUserId() != null) {
            // 단일 수신자 처리
            sendSingle(dto);
        }
    }

    private NotificationRequestDto copyForSingleUser(NotificationRequestDto bulkDto, Long userId) {
        return NotificationRequestDto.builder()
                .userId(userId)
                .type(bulkDto.getType())
                .title(bulkDto.getTitle())
                .body(bulkDto.getBody())
                .deeplink(bulkDto.getDeeplink())
                .scheduledAt(bulkDto.getScheduledAt())
                .resourceType(bulkDto.getResourceType())
                .dedupeKey(bulkDto.getDedupeKey() != null ? bulkDto.getDedupeKey() + "_" + userId : null)
                .resourceId(bulkDto.getResourceId())
                .actorUserId(bulkDto.getActorUserId())
                .build();
    }

    private void sendSingle(NotificationRequestDto dto) {
        boolean isScheduled = dto.getScheduledAt() != null;

        // 타입 파싱
        com.onmeet.notification.type.NotificationType type = 
            com.onmeet.notification.type.NotificationType.valueOf(dto.getType());
        com.onmeet.notification.type.ResourceType resType = 
            dto.getResourceType() != null ? com.onmeet.notification.type.ResourceType.valueOf(dto.getResourceType()) : com.onmeet.notification.type.ResourceType.SYSTEM;

        // 템플릿 기반 메시지 렌더링
        NotificationTemplate template = NotificationTemplate.fromType(type);
        Map<String, String> params = buildTemplateParams(dto);

        String renderedTitle = template.getDefaultTitle();
        String renderedBody = template.renderBody(params);

        // 외부에서 직접 지정한 title/body가 있으면 우선 사용 (하위 호환)
        String finalTitle = (dto.getTitle() != null && !dto.getTitle().isBlank()) ? dto.getTitle() : renderedTitle;
        String finalBody = (dto.getBody() != null && !dto.getBody().isBlank()) ? dto.getBody() : renderedBody;

        // dedupeKey 중복 체크
        if (dto.getDedupeKey() != null && notificationRepository.existsByDedupeKey(dto.getDedupeKey())) {
            log.info("Duplicate notification detected via dedupeKey: {}. Skipping.", dto.getDedupeKey());
            return;
        }

        Notification notification = Notification.builder()
                .type(type)
                .title(finalTitle)
                .body(finalBody)
                .deeplink(dto.getDeeplink())
                .scheduledAt(dto.getScheduledAt())
                .resourceType(resType)
                .dedupeKey(dto.getDedupeKey())
                .resourceId(dto.getResourceId() != null ? dto.getResourceId() : "0")
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

            boolean sseSent = sendToClient(dto.getUserId(), recipient);
            if (sseSent) {
                recipient.markAsSent();
            }

            // auth-service에서 디바이스 토큰 동기 조회 및 푸시 발송
            try {
                AuthServiceClient.UserInfoResponse userInfo = authServiceClient.getUserInfo(dto.getUserId());
                if (userInfo != null && userInfo.fcmDeviceToken() != null && !userInfo.fcmDeviceToken().isBlank()) {
                    fcmService.sendPushToToken(userInfo.fcmDeviceToken(), notification.getTitle(),
                            notification.getBody(), notification.getDeeplink());
                }
                
                // notification-service 로컬 DB에 저장된 토큰들로도 발송 (기존 로직 유지)
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
    public boolean sendToClient(Long userId, NotificationRecipient recipient) {
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
                        .id(String.valueOf(recipient.getNotification().getId()))
                        .name("notification")
                        .data(NotificationResponseDto.from(recipient)));
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

    /**
     * 템플릿 렌더링에 필요한 파라미터를 구성합니다.
     * actorUserId → senderName, userId → receiverName, title → title
     */
    private Map<String, String> buildTemplateParams(NotificationRequestDto dto) {
        Map<String, String> params = new HashMap<>();

        // 발신자 이름 조회
        if (dto.getActorUserId() != null) {
            params.put("senderName", authServiceClient.getUserName(dto.getActorUserId()));
        } else {
            params.put("senderName", "알 수 없는 사용자");
        }

        // 수신자 이름 조회
        if (dto.getUserId() != null) {
            params.put("receiverName", authServiceClient.getUserName(dto.getUserId()));
        } else {
            params.put("receiverName", "알 수 없는 사용자");
        }

        // Kafka Producer(예: video-service)에서 이벤트 발행 시 title 값을 DTO에 담아서 보내도록 스펙 정의됨
        // -> 알림 서비스에서 동기적으로 외부 API를 찔러 방 제목을 조회하는 것은 지양(결합도 및 병목 방지)
        params.put("title", dto.getTitle() != null ? dto.getTitle() : "");

        // 원본 body (SYSTEM, EVENT 템플릿에서 사용)
        params.put("body", dto.getBody() != null ? dto.getBody() : "");

        return params;
    }
}
