package com.onmeet.ai.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.dto.event.AudioChunkReadyEvent;
import com.onmeet.ai.service.SttWorkerService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class AudioChunkConsumer {

    private final ObjectMapper om;
    private final SttWorkerService sttWorker;

    public AudioChunkConsumer(ObjectMapper om, SttWorkerService sttWorker) {
        this.om = om;
        this.sttWorker = sttWorker;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.audio-chunk-ready}",
            groupId = "ai-stt-worker"
    )
    public void onMessage(String message, Acknowledgment ack) {
        try {
            AudioChunkReadyEvent event = om.readValue(message, AudioChunkReadyEvent.class);
            sttWorker.handleAudioChunk(event);
            ack.acknowledge();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
