package com.onmeet.ai.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.common.dto.event.ChatMessageEvent;
import com.onmeet.ai.service.TranscriptBuilderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ChatEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(ChatEventsConsumer.class);

    private final TranscriptBuilderService builder;
    private final ObjectMapper objectMapper;

    public ChatEventsConsumer(TranscriptBuilderService builder, ObjectMapper objectMapper) {
        this.builder = builder;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.chat-events}",
            groupId = "ai-transcript-builder"
    )
    public void onMessage(String message) {
        try {
            ChatMessageEvent event = objectMapper.readValue(message, ChatMessageEvent.class);
            builder.ingestChat(event);
        } catch (Exception e) {
            log.error("Failed to process chat event: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
