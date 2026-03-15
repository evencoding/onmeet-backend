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
import java.util.Collections;
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
        // 공통 파라미터 미리 조회 (N+1 방지)
        String actorName = "알 수 없는 사용자";
        if (dto.getActorUserId() != null) {
            actorName = authServiceClient.getUserName(dto.getActorUserId());
        }

        if (dto.getUserIds() != null && !dto.getUserIds().isEmpty()) {
            // 다수 수신자 처리 (N+1 방지: 배치 조회)
            List<Long> targetUserIds = dto.getUserIds();
            List<AuthServiceClient.UserInfoResponse> userInfos = authServiceClient.getBatchUserInfo(targetUserIds);
            Map<Long, String> userTokenMap = userInfos.stream()
                    // TODO: [Auth 담당자] UserInfoDto 배치 조회 시 'fcmDeviceToken' 필드를 응답에 포함해주시면 이 필드를 통해 최신 토큰 발송이 가능해집니다.
                    .filter(u -> u.fcmDeviceToken() != null && !u.fcmDeviceToken().isBlank())
                    .collect(Collectors.toMap(AuthServiceClient.UserInfoResponse::userId, AuthServiceClient.UserInfoResponse::fcmDeviceToken, (a, b) -> a));

            for (Long userId : targetUserIds) {
                NotificationRequestDto singleDto = copyForSingleUser(dto, userId);
                String latestToken = userTokenMap.get(userId);
                sendSingle(singleDto, actorName, latestToken);
            }
        } else if (dto.getUserId() != null) {
            // 단일 수신자 처리
            String latestToken = null;
            try {
                AuthServiceClient.UserInfoResponse userInfo = authServiceClient.getUserInfo(dto.getUserId());
                if (userInfo != null) {
                    // TODO: [Auth 담당자] UserInfoDto 단건 조회 시 'fcmDeviceToken' 필드를 응답에 포함해주시면 이 필드를 통해 최신 토큰 발송이 가능해집니다.
                    latestToken = userInfo.fcmDeviceToken();
                }
            } catch (Exception e) {
                log.warn("Failed to fetch latest token for userId={}", dto.getUserId());
            }
            sendSingle(dto, actorName, latestToken);
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

    private void sendSingle(NotificationRequestDto dto, String actorName, String latestToken) {
        boolean isScheduled = dto.getScheduledAt() != null;

        // 타입 파싱 (DoS 방지)
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

        // 템플릿 기반 메시지 렌더링
        NotificationTemplate template = NotificationTemplate.fromType(type);
        Map<String, String> params = buildTemplateParams(dto, actorName);

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

            boolean sseSent = sendToClient(dto.getUserId(), recipient);
            if (sseSent) {
                recipient.markAsSent();
            }

            // FCM 푸시 발송 (Auth 최신 토큰 + 로컬 DB 토큰의 합집합으로 중복 방지)
            try {
                sendFcmPushToUniqueTokens(dto.getUserId(), notification, latestToken);
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

    /**
     * 특정 디바이스 토큰으로 푸시를 발송합니다. (인프라 계층 위임)
     */
    public void sendFcmPushToToken(String token, Notification notification) {
        fcmService.sendPushToToken(token, notification.getTitle(),
                notification.getBody(), notification.getDeeplink());
    }

    /**
     * 로컬 DB에 저장된 유저의 모든 토큰으로 푸시를 발송합니다.
     */
    public void sendFcmPushLocal(Long userId, Notification notification) {
        fcmService.sendPushToLocalTokens(userId, notification.getTitle(),
                notification.getBody(), notification.getDeeplink());
    }

    /**
     * Auth 서비스에서 받은 최신 토큰과 로컬 DB의 토큰들을 합쳐서 중복 없이 발송합니다.
     */
    public void sendFcmPushToUniqueTokens(Long userId, Notification notification, String latestToken) {
        Set<String> uniqueTokens = new HashSet<>();

        // 1. Auth 서비스에서 받은 최신 토큰 추가
        if (latestToken != null && !latestToken.isBlank()) {
            uniqueTokens.add(latestToken);
        }

        // 2. 로컬 DB에 저장된 토큰들 추가 (중복은 Set에 의해 자동 제거됨)
        uniqueTokens.addAll(fcmService.getTokensByUserId(userId));

        if (uniqueTokens.isEmpty()) {
            log.debug("No FCM tokens found for userId={}", userId);
            return;
        }

        // 3. 유니크한 토큰들에 대해서만 발송
        for (String token : uniqueTokens) {
            fcmService.sendPushToToken(token, notification.getTitle(),
                    notification.getBody(), notification.getDeeplink());
        }
    }

    /**
     * 유저의 정보를 조회하여 최신 토큰(Auth)과 로컬 토큰으로 중복 없이 발송합니다.
     */
    public void sendFcmPush(Long userId, Notification notification) {
        String latestToken = null;
        try {
            AuthServiceClient.UserInfoResponse userInfo = authServiceClient.getUserInfo(userId);
            if (userInfo != null) {
                // TODO: [Auth 담당자] UserInfoDto 조회 시 'fcmDeviceToken' 필드를 응답에 포함해주시면 스케줄러에서도 최신 토큰으로 발송이 가능해집니다.
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
    private Map<String, String> buildTemplateParams(NotificationRequestDto dto, String actorName) {
        Map<String, String> params = new HashMap<>();

        // 발신자 이름 (이미 조회됨)
        params.put("senderName", actorName);

        // 수신자 이름 조회: auth-service에서 실제 이름을 가져옴. 실패 시 "사용자"로 폴백
        String receiverName = "사용자";
        if (dto.getUserId() != null) {
            try {
                AuthServiceClient.UserInfoResponse receiverInfo = authServiceClient.getUserInfo(dto.getUserId());
                if (receiverInfo != null && receiverInfo.name() != null && !receiverInfo.name().isBlank()) {
                    receiverName = receiverInfo.name();
                }
            } catch (Exception e) {
                log.warn("Failed to fetch receiver name for userId={}, using fallback '사용자'", dto.getUserId());
            }
        }
        params.put("receiverName", receiverName);

        // Kafka Producer(예: video-service)에서 이벤트 발행 시 title 값을 DTO에 담아서 보내도록 스펙 정의됨
        // -> 알림 서비스에서 동기적으로 외부 API를 찔러 방 제목을 조회하는 것은 지양(결합도 및 병목 방지)
        params.put("title", dto.getTitle() != null ? dto.getTitle() : "");

        // 원본 body (SYSTEM, EVENT 템플릿에서 사용)
        params.put("body", dto.getBody() != null ? dto.getBody() : "");

        return params;
    }
}
