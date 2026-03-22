package com.onmeet.ai.messaging.consumer;

import com.onmeet.ai.dto.event.MeetingEndedEvent;
import com.onmeet.ai.service.TranscriptBuilderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MeetingEndedConsumer {

    private final TranscriptBuilderService builder;

    public MeetingEndedConsumer(TranscriptBuilderService builder) {
        this.builder = builder;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.meeting-ended}",
            groupId = "ai-transcript-builder"
    )
    public void onMessage(MeetingEndedEvent event) {
        try {
            builder.finalizeMeeting(event.getRoomId(), event.getHostUserId(), event.getEndedAt());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
