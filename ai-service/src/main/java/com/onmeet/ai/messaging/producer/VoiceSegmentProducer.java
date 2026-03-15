package com.onmeet.ai.messaging.producer;

import com.onmeet.ai.dto.event.VoiceSegmentCreatedEvent;
import com.onmeet.common.exception.BusinessException;
import com.onmeet.common.exception.errorcode.AiErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class VoiceSegmentProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public VoiceSegmentProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.voice-segment-created}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(VoiceSegmentCreatedEvent event) {
        try {
            kafkaTemplate.send(topic, String.valueOf(event.getRoomId()), event);
        } catch (Exception e) {
            log.error("Failed to publish voice segment event: roomId={}, topic={}, error={}", 
                    event.getRoomId(), topic, e.getMessage(), e);
            throw new BusinessException(AiErrorCode.EVENT_PUBLISH_FAILED);
        }
    }
}
