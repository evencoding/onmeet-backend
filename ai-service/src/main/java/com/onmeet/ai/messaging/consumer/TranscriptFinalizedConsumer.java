package com.onmeet.ai.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.dto.event.TranscriptFinalizedEvent;
import com.onmeet.ai.service.SummaryWorkerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TranscriptFinalizedConsumer {

    private static final Logger log = LoggerFactory.getLogger(TranscriptFinalizedConsumer.class);

    private final SummaryWorkerService worker;
    private final ObjectMapper objectMapper;

    public TranscriptFinalizedConsumer(SummaryWorkerService worker, ObjectMapper objectMapper) {
        this.worker = worker;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.transcript-finalized}",
            groupId = "ai-summary-worker"
    )
    public void onMessage(String message) {
        try {
            TranscriptFinalizedEvent event = objectMapper.readValue(message, TranscriptFinalizedEvent.class);
            log.info("Received transcript.finalized event: roomId={}, transcriptId={}", event.getRoomId(), event.getTranscriptId());
            worker.handleTranscriptFinalized(event);
        } catch (Exception e) {
            log.error("Failed to process transcript.finalized event: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
