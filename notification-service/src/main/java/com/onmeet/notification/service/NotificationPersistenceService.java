package com.onmeet.notification.service;

import com.onmeet.notification.dto.NotificationRequestDto;
import com.onmeet.notification.entity.Notification;
import com.onmeet.notification.entity.NotificationRecipient;
import com.onmeet.notification.entity.NotificationStream;
import com.onmeet.notification.repository.NotificationRecipientRepository;
import com.onmeet.notification.repository.NotificationRepository;
import com.onmeet.notification.repository.NotificationStreamRepository;
import com.onmeet.notification.type.NotificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * DB 트랜잭션이 필요한 알림 영속 작업을 담당합니다.
 * NotificationService에서 분리하여 @Transactional 프록시가 정상 동작하도록 합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPersistenceService {

    private final NotificationRepository notificationRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final NotificationStreamRepository streamRepository;

    @Transactional
    public NotificationRecipient saveNotification(NotificationRequestDto dto,
            com.onmeet.notification.type.NotificationType type,
            String title, String body,
            com.onmeet.notification.type.ResourceType resType, boolean isScheduled) {
        if (dto.getDedupeKey() != null && notificationRepository.existsByDedupeKey(dto.getDedupeKey())) {
            log.info("Duplicate notification detected via dedupeKey: {}. Skipping.", dto.getDedupeKey());
            return null;
        }

        Notification notification = Notification.builder()
                .type(type)
                .title(title)
                .body(body)
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
        return recipient;
    }

    @Transactional
    public void markRecipientAsSent(NotificationRecipient recipient) {
        recipient.markAsSent();
        recipientRepository.save(recipient);
    }

    @Transactional
    public void saveNotificationStream(Long userId, String streamId) {
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

    @Transactional
    public void removeNotificationStream(String streamId) {
        try {
            streamRepository.deleteById(streamId);
        } catch (Exception e) {
            log.error("Failed to remove notification stream: {}", streamId, e);
        }
    }
}
