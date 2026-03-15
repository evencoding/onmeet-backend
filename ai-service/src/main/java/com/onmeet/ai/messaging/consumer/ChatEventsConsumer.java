package com.onmeet.ai.messaging.consumer;

import com.onmeet.ai.dto.event.ChatMessageEvent;
import com.onmeet.ai.service.TranscriptBuilderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ChatEventsConsumer {

    private final TranscriptBuilderService builder;

    public ChatEventsConsumer(TranscriptBuilderService builder) {
        this.builder = builder;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.chat-events}",
            groupId = "ai-transcript-builder"
    )
    public void onMessage(ChatMessageEvent event) {
        try {
            builder.ingestChat(event);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
