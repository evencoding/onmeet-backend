package com.onmeet.notification.consumer;

import com.onmeet.notification.dto.NotificationRequestDto;
import com.onmeet.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka로부터 알림 이벤트를 수신하는 Consumer.
 * <p>
 * 토픽: notification.send 발행 주체: video-service, auth-service 등 각 마이크로서비스
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "notification.send", groupId = "notification-service", containerFactory = "kafkaListenerContainerFactory")
    public void consume(
            @Payload NotificationRequestDto dto,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("[Kafka] Received notification event: topic={}, partition={}, offset={}, userId={}, type={}",
                topic, partition, offset, dto.getUserId(), dto.getType());
        try {
            notificationService.send(dto);
        } catch (Exception e) {
            log.error("[Kafka] Failed to process notification event: userId={}, type={}, error={}",
                    dto.getUserId(), dto.getType(), e.getMessage(), e);
            // 처리 실패 시 예외를 다시 던져 Kafka가 재시도(retry) 또는 DLT로 보내도록 함
            throw e;
        }
    }
}
