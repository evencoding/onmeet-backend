package com.onmeet.ai.messaging.producer;

import com.onmeet.common.dto.NotificationRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 알림 서비스(Notification Service)로 Kafka 이벤트를 발생시켜 비동기 알림을 전송합니다.
     */
    public void publishNotification(NotificationRequestDto request) {
        try {
            kafkaTemplate.send("notification.send", request);
            log.info("Successfully published notification event for User ID: {}, Type: {}", request.getUserId(), request.getType());
        } catch (Exception e) {
            log.error("Failed to publish notification event for User ID: {}, Type: {}", request.getUserId(), request.getType(), e);
        }
    }
}
