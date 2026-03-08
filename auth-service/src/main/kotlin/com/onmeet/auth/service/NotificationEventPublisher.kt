package com.onmeet.auth.service

import com.onmeet.common.dto.NotificationRequestDto
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.slf4j.LoggerFactory

@Service
class NotificationEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    private val log = LoggerFactory.getLogger(NotificationEventPublisher::class.java)

    /**
     * 알림 서비스(Notification Service)로 Kafka 이벤트를 발생시켜 비동기 알림을 전송합니다.
     */
    fun publishNotification(request: NotificationRequestDto) {
        try {
            kafkaTemplate.send("notification.send", request)
            log.info("Successfully published notification event for User ID: ${request.userId}, Type: ${request.type}")
        } catch (e: Exception) {
            log.error("Failed to publish notification event for User ID: ${request.userId}, Type: ${request.type}", e)
        }
    }
}
