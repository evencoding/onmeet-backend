package com.onmeet.ai.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.dto.event.MeetingEndedEvent;
import com.onmeet.ai.service.TranscriptBuilderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class MeetingEndedConsumer {

    private final ObjectMapper om;
    private final TranscriptBuilderService builder;

    public MeetingEndedConsumer(ObjectMapper om, TranscriptBuilderService builder) {
        this.om = om;
        this.builder = builder;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.meeting-ended}",
            groupId = "ai-transcript-builder"
    )
    public void onMessage(String message, Acknowledgment ack) {
        try {
            MeetingEndedEvent event = om.readValue(message, MeetingEndedEvent.class);
            builder.finalizeMeeting(event.getRoomId(), event.getHostUserId(), event.getEndedAt());
            ack.acknowledge();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
