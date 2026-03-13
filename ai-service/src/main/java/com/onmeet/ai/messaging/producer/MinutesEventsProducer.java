package com.onmeet.ai.messaging.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.dto.event.MinutesGeneratedEvent;
import com.onmeet.common.exception.BusinessException;
import com.onmeet.common.exception.errorcode.AiErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class MinutesEventsProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper om;
    private final String topic;

    public MinutesEventsProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper om,
            @Value("${app.kafka.topics.minutes-generated}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.om = om;
        this.topic = topic;
    }

    public void publish(MinutesGeneratedEvent event) {
        try {
            kafkaTemplate.send(topic, String.valueOf(event.getRoomId()), om.writeValueAsString(event));
        } catch (Exception e) {
            // TODO: [AI][AiErrorCode.EVENT_PUBLISH_FAILED] 에러메시지 검수 요청
            throw new BusinessException(AiErrorCode.EVENT_PUBLISH_FAILED);
        }
    }
}
