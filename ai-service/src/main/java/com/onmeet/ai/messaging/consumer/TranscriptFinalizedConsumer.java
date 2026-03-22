package com.onmeet.ai.messaging.consumer;

import com.onmeet.ai.dto.event.TranscriptFinalizedEvent;
import com.onmeet.ai.service.SummaryWorkerService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TranscriptFinalizedConsumer {

    private final SummaryWorkerService worker;

    public TranscriptFinalizedConsumer(SummaryWorkerService worker) {
        this.worker = worker;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.transcript-finalized}",
            groupId = "ai-summary-worker"
    )
    public void onMessage(TranscriptFinalizedEvent event) {
        try {
            worker.handleTranscriptFinalized(event);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
