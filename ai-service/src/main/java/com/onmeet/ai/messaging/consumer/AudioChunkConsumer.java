package com.onmeet.ai.messaging.consumer;

import com.onmeet.common.dto.event.AudioChunkReadyEvent;
import com.onmeet.ai.service.SttWorkerService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AudioChunkConsumer {

    private final SttWorkerService sttWorker;

    public AudioChunkConsumer(SttWorkerService sttWorker) {
        this.sttWorker = sttWorker;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.audio-chunk-ready}",
            groupId = "ai-stt-worker"
    )
    public void onMessage(AudioChunkReadyEvent event) {
        try {
            sttWorker.handleAudioChunk(event);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
