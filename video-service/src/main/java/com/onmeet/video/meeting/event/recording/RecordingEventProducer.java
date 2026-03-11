package com.onmeet.video.meeting.event.recording;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class RecordingEventProducer {

    private static final Logger log = LoggerFactory.getLogger(RecordingEventProducer.class);
    private static final String TOPIC = "recording-completed";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public RecordingEventProducer(KafkaTemplate<String, Object> kafkaTemplate,
                                  ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishRecordingCompleted(RecordingCompletedEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, String.valueOf(event.roomId()), json);
            log.info("Published recording-completed event: roomId={}, recordingId={}, s3Path={}",
                    event.roomId(), event.recordingId(), event.s3Path());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize recording-completed event: roomId={}, recordingId={}",
                    event.roomId(), event.recordingId(), e);
        }
    }
}
