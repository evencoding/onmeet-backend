package com.onmeet.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.notification.dto.NotificationRequestDto;
import com.onmeet.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka로부터 알림 이벤트를 수신하는 Consumer.
 * <p>
 * 토픽: notification.send 발행 주체: video-service, auth-service 등 각 마이크로서비스
 * String으로 수신 후 ObjectMapper로 역직렬화 (common DTO 패키지 불일치 방지)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "notification.send", groupId = "notification-service")
    public void consume(String message) {
        try {
            NotificationRequestDto dto = objectMapper.readValue(message, NotificationRequestDto.class);
            log.info("[Kafka] Received notification event: userId={}, userIds={}, type={}",
                    dto.getUserId(), dto.getUserIds(), dto.getType());
            notificationService.send(dto);
        } catch (Exception e) {
            log.error("[Kafka] Failed to process notification event: error={}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
