package com.onmeet.ai.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.dto.event.VoiceSegmentCreatedEvent;
import com.onmeet.ai.service.TranscriptBuilderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class VoiceSegmentConsumer {

    private final ObjectMapper om;
    private final TranscriptBuilderService builder;

    public VoiceSegmentConsumer(ObjectMapper om, TranscriptBuilderService builder) {
        this.om = om;
        this.builder = builder;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.voice-segment-created}",
            groupId = "ai-transcript-builder"
    )
    public void onMessage(String message, Acknowledgment ack) {
        try {
            VoiceSegmentCreatedEvent event = om.readValue(message, VoiceSegmentCreatedEvent.class);
            builder.ingestVoice(event);
            ack.acknowledge();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
