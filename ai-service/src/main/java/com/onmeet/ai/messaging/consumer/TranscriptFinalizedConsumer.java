package com.onmeet.ai.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.dto.event.TranscriptFinalizedEvent;
import com.onmeet.ai.service.SummaryWorkerService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class TranscriptFinalizedConsumer {

    private final ObjectMapper om;
    private final SummaryWorkerService worker;

    public TranscriptFinalizedConsumer(ObjectMapper om, SummaryWorkerService worker) {
        this.om = om;
        this.worker = worker;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.transcript-finalized}",
            groupId = "ai-summary-worker"
    )
    public void onMessage(String message, Acknowledgment ack) {
        try {
            TranscriptFinalizedEvent event = om.readValue(message, TranscriptFinalizedEvent.class);
            worker.handleTranscriptFinalized(event);
            ack.acknowledge();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
