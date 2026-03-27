package com.onmeet.ai.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.dto.event.VoiceSegmentCreatedEvent;
import com.onmeet.ai.service.TranscriptBuilderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class VoiceSegmentConsumer {

    private static final Logger log = LoggerFactory.getLogger(VoiceSegmentConsumer.class);

    private final TranscriptBuilderService builder;
    private final ObjectMapper objectMapper;

    public VoiceSegmentConsumer(TranscriptBuilderService builder, ObjectMapper objectMapper) {
        this.builder = builder;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.voice-segment-created}",
            groupId = "ai-transcript-builder"
    )
    public void onMessage(String message) {
        try {
            VoiceSegmentCreatedEvent event = objectMapper.readValue(message, VoiceSegmentCreatedEvent.class);
            builder.ingestVoice(event);
        } catch (Exception e) {
            log.error("Failed to process voice.segment.created event: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
