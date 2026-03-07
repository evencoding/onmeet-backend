package com.onmeet.ai.messaging.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.dto.event.TranscriptFinalizedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class TranscriptEventsProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper om;
    private final String topic;

    public TranscriptEventsProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper om,
            @Value("${app.kafka.topics.transcript-finalized}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.om = om;
        this.topic = topic;
    }

    public void publish(TranscriptFinalizedEvent event) {
        try {
            kafkaTemplate.send(topic, String.valueOf(event.getRoomId()), om.writeValueAsString(event));
        } catch (Exception e) {
            throw new IllegalStateException("failed to publish transcript.finalized", e);
        }
    }
}
