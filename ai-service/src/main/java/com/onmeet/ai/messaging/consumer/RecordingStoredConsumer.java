package com.onmeet.ai.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.dto.event.RecordingStoredEvent;
import com.onmeet.ai.service.RecordingProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class RecordingStoredConsumer {

    private static final Logger log = LoggerFactory.getLogger(RecordingStoredConsumer.class);

    private final ObjectMapper om;
    private final RecordingProcessingService recordingProcessingService;

    public RecordingStoredConsumer(ObjectMapper om, RecordingProcessingService recordingProcessingService) {
        this.om = om;
        this.recordingProcessingService = recordingProcessingService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.recording-stored}",
            groupId = "ai-recording-processor"
    )
    public void onMessage(String message, Acknowledgment ack) {
        try {
            RecordingStoredEvent event = om.readValue(message, RecordingStoredEvent.class);
            log.info("Received recording-stored event: roomId={}, recordingId={}, s3Path={}",
                    event.getRoomId(), event.getRecordingId(), event.getS3Path());
            recordingProcessingService.processRecording(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process recording-stored event", e);
            throw new RuntimeException(e);
        }
    }
}
