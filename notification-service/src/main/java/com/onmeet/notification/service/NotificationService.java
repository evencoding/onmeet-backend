package com.onmeet.notification.service;

import com.onmeet.notification.dto.NotificationRequestDto;
import com.onmeet.notification.dto.NotificationResponseDto;
import com.onmeet.notification.entity.Notification;
import com.onmeet.notification.entity.NotificationRecipient;
import com.onmeet.notification.infra.AuthServiceClient;
import com.onmeet.notification.type.NotificationTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationPersistenceService persistenceService;
    private final NotificationSettingService settingService;
    private final FcmService fcmService;
    private final AuthServiceClient authServiceClient;

    // In-memory storage for active emitters (for real-time push)
    // Structure: Map<UserId, Map<EmitterId, SseEmitter>>
    // This allows multiple connections per user (multi-tab/device support)
    private final Map<Long, Map<String, SseEmitter>> emitters = new ConcurrentHashMap<>();

    private static final Long DEFAULT_TIMEOUT = 60L * 1000 * 60; // 60 minutes
    private static final long HEARTBEAT_INTERVAL = 5_000L; // 5 seconds (gateway 10s timeout 이내)

    // ──────────────────────────────────────────────
    // SSE Subscribe
    // ──────────────────────────────────────────────

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        // Unique ID for this connection: userId + UUID (collision-safe)
        String emitterId = userId + "_" + java.util.UUID.randomUUID();

        persistenceService.saveNotificationStream(userId, emitterId);

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
        persistenceService.removeNotificationStream(emitterId);
        log.debug("SSE connection removed: userId={}, emitterId={}", userId, emitterId);
    }

    // ──────────────────────────────────────────────
    // 알림 전송 (즉시 / 예약)
    // ──────────────────────────────────────────────

    public void send(NotificationRequestDto dto) {
        // HTTP 호출은 트랜잭션 밖에서 수행 (DB 커넥션 점유 방지)
        String actorName = "시스템";
        if (dto.getActorUserId() != null && dto.getActorUserId() > 0) {
            actorName = authServiceClient.getUserName(dto.getActorUserId());
        }

        if (dto.getUserIds() != null && !dto.getUserIds().isEmpty()) {
            List<Long> targetUserIds = dto.getUserIds();
            List<AuthServiceClient.UserInfoResponse> userInfos = authServiceClient.getBatchUserInfo(targetUserIds);
            Map<Long, String> userTokenMap = userInfos.stream()
                    .filter(u -> u.fcmDeviceToken() != null && !u.fcmDeviceToken().isBlank())
                    .collect(Collectors.toMap(AuthServiceClient.UserInfoResponse::userId, AuthServiceClient.UserInfoResponse::fcmDeviceToken, (a, b) -> a));
            Map<Long, String> userNameMap = userInfos.stream()
                    .filter(u -> u.name() != null && !u.name().isBlank())
                    .collect(Collectors.toMap(AuthServiceClient.UserInfoResponse::userId, AuthServiceClient.UserInfoResponse::name, (a, b) -> a));

            for (Long userId : targetUserIds) {
                NotificationRequestDto singleDto = copyForSingleUser(dto, userId);
                String latestToken = userTokenMap.get(userId);
                String receiverName = userNameMap.getOrDefault(userId, "사용자");
                sendSingle(singleDto, actorName, latestToken, receiverName);
            }
        } else if (dto.getUserId() != null) {
            String latestToken = null;
            String receiverName = "사용자";
            try {
                AuthServiceClient.UserInfoResponse userInfo = authServiceClient.getUserInfo(dto.getUserId());
                if (userInfo != null) {
                    latestToken = userInfo.fcmDeviceToken();
                    if (userInfo.name() != null && !userInfo.name().isBlank()) {
                        receiverName = userInfo.name();
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to fetch latest token for userId={}", dto.getUserId());
            }
            sendSingle(dto, actorName, latestToken, receiverName);
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

    private void sendSingle(NotificationRequestDto dto, String actorName, String latestToken, String receiverName) {
        boolean isScheduled = dto.getScheduledAt() != null;

        if (dto.getType() == null) {
            log.warn("Notification type is null. Skipping.");
            return;
        }

        com.onmeet.notification.type.NotificationType type;
        try {
            type = com.onmeet.notification.type.NotificationType.valueOf(dto.getType());
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid notification type: {}. Skipping.", dto.getType());
            return;
        }

        com.onmeet.notification.type.ResourceType resType = com.onmeet.notification.type.ResourceType.SYSTEM;
        if (dto.getResourceType() != null) {
            try {
                resType = com.onmeet.notification.type.ResourceType.valueOf(dto.getResourceType());
            } catch (IllegalArgumentException ex) {
                log.warn("Invalid resource type: {}. Using SYSTEM.", dto.getResourceType());
            }
        }

        NotificationTemplate template = NotificationTemplate.fromType(type);
        Map<String, String> params = buildTemplateParams(dto, actorName, receiverName);

        String renderedTitle = template.getDefaultTitle();
        String renderedBody = template.renderBody(params);

        String finalTitle = (dto.getTitle() != null && !dto.getTitle().isBlank()) ? dto.getTitle() : renderedTitle;
        String finalBody = (dto.getBody() != null && !dto.getBody().isBlank()) ? dto.getBody() : renderedBody;

        // DB 작업: @Transactional 프록시를 통해 커넥션 즉시 반환
        NotificationRecipient recipient = persistenceService.saveNotification(dto, type, finalTitle, finalBody, resType, isScheduled);
        if (recipient == null) return; // dedupeKey 중복

        // 즉시 발송: DB 트랜잭션 밖에서 SSE/FCM 발송 (커넥션 점유 없음)
        if (!isScheduled) {
            if (!settingService.shouldSendNotification(dto.getUserId(), recipient.getNotification().getType())) {
                log.info("Notification blocked by user settings: userId={}, type={}",
                        dto.getUserId(), recipient.getNotification().getType());
                return;
            }

            boolean sseSent = sendToClient(dto.getUserId(), recipient);
            if (sseSent) {
                persistenceService.markRecipientAsSent(recipient);
            }

            try {
                sendFcmPushToUniqueTokens(dto.getUserId(), recipient.getNotification(), latestToken);
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

    public void sendFcmPushToToken(String token, Notification notification) {
        fcmService.sendPushToToken(token, notification.getTitle(),
                notification.getBody(), notification.getDeeplink());
    }

    public void sendFcmPushLocal(Long userId, Notification notification) {
        fcmService.sendPushToLocalTokens(userId, notification.getTitle(),
                notification.getBody(), notification.getDeeplink());
    }

    public void sendFcmPushToUniqueTokens(Long userId, Notification notification, String latestToken) {
        Set<String> uniqueTokens = new HashSet<>();

        if (latestToken != null && !latestToken.isBlank()) {
            uniqueTokens.add(latestToken);
        }

        uniqueTokens.addAll(fcmService.getTokensByUserId(userId));

        if (uniqueTokens.isEmpty()) {
            log.debug("No FCM tokens found for userId={}", userId);
            return;
        }

        for (String token : uniqueTokens) {
            fcmService.sendPushToToken(token, notification.getTitle(),
                    notification.getBody(), notification.getDeeplink());
        }
    }

    public void sendFcmPush(Long userId, Notification notification) {
        String latestToken = null;
        try {
            AuthServiceClient.UserInfoResponse userInfo = authServiceClient.getUserInfo(userId);
            if (userInfo != null) {
                latestToken = userInfo.fcmDeviceToken();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch latest token for notification: userId={}", userId);
        }

        sendFcmPushToUniqueTokens(userId, notification, latestToken);
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
                    log.debug("Heartbeat failed for user={}, emitterId={}", userId, emitterId);
                    removeEmitter(userId, emitterId);
                }
            });
        });
    }

    // ──────────────────────────────────────────────
    // Template Params
    // ──────────────────────────────────────────────

    private Map<String, String> buildTemplateParams(NotificationRequestDto dto, String actorName, String receiverName) {
        Map<String, String> params = new HashMap<>();
        params.put("senderName", actorName);
        params.put("receiverName", receiverName);
        params.put("title", dto.getTitle() != null ? dto.getTitle() : "");
        params.put("body", dto.getBody() != null ? dto.getBody() : "");
        return params;
    }
}
