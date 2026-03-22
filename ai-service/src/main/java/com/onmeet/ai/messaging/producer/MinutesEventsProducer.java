package com.onmeet.ai.messaging.producer;

import com.onmeet.ai.dto.event.MinutesGeneratedEvent;
import com.onmeet.common.exception.BusinessException;
import com.onmeet.common.exception.errorcode.AiErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class MinutesEventsProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public MinutesEventsProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.minutes-generated}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(MinutesGeneratedEvent event) {
        try {
            kafkaTemplate.send(topic, String.valueOf(event.getRoomId()), event);
        } catch (Exception e) {
            // TODO: [AI][AiErrorCode.EVENT_PUBLISH_FAILED] 에러메시지 검수 요청
            throw new BusinessException(AiErrorCode.EVENT_PUBLISH_FAILED);
        }
    }
}
