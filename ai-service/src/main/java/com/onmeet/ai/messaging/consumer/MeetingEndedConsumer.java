package com.onmeet.ai.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.dto.event.MeetingEndedEvent;
import com.onmeet.ai.service.TranscriptBuilderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MeetingEndedConsumer {

    private static final Logger log = LoggerFactory.getLogger(MeetingEndedConsumer.class);

    private final TranscriptBuilderService builder;
    private final ObjectMapper objectMapper;

    public MeetingEndedConsumer(TranscriptBuilderService builder, ObjectMapper objectMapper) {
        this.builder = builder;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.meeting-ended}",
            groupId = "ai-transcript-builder"
    )
    public void onMessage(String message) {
        try {
            MeetingEndedEvent event = objectMapper.readValue(message, MeetingEndedEvent.class);
            log.info("Received meeting.ended event: roomId={}, title={}, waiting 30s for STT completion...",
                    event.getRoomId(), event.getTitle());
            Thread.sleep(30_000);
            builder.finalizeMeeting(event);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Meeting ended processing interrupted: {}", ie.getMessage());
        } catch (Exception e) {
            log.error("Failed to process meeting.ended event: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
