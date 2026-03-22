package com.onmeet.ai.messaging.consumer;

import com.onmeet.ai.dto.event.VoiceSegmentCreatedEvent;
import com.onmeet.ai.service.TranscriptBuilderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class VoiceSegmentConsumer {

    private final TranscriptBuilderService builder;

    public VoiceSegmentConsumer(TranscriptBuilderService builder) {
        this.builder = builder;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.voice-segment-created}",
            groupId = "ai-transcript-builder"
    )
    public void onMessage(VoiceSegmentCreatedEvent event) {
        try {
            builder.ingestVoice(event);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
