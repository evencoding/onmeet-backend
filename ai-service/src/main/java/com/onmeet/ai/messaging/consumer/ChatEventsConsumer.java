package com.onmeet.ai.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.dto.event.ChatMessageEvent;
import com.onmeet.ai.service.TranscriptBuilderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class ChatEventsConsumer {

    private final ObjectMapper om;
    private final TranscriptBuilderService builder;

    public ChatEventsConsumer(ObjectMapper om, TranscriptBuilderService builder) {
        this.om = om;
        this.builder = builder;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.chat-events}",
            groupId = "ai-transcript-builder"
    )
    public void onMessage(String message, Acknowledgment ack) {
        try {
            ChatMessageEvent event = om.readValue(message, ChatMessageEvent.class);
            builder.ingestChat(event);
            ack.acknowledge();
        } catch (Exception e) {
            // ack 안 하면 재처리됨
            throw new RuntimeException(e);
        }
    }
}
