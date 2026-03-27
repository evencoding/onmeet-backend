package com.onmeet.ai.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.common.dto.event.AudioChunkReadyEvent;
import com.onmeet.ai.service.SttWorkerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AudioChunkConsumer {

    private static final Logger log = LoggerFactory.getLogger(AudioChunkConsumer.class);

    private final SttWorkerService sttWorker;
    private final ObjectMapper objectMapper;

    public AudioChunkConsumer(SttWorkerService sttWorker, ObjectMapper objectMapper) {
        this.sttWorker = sttWorker;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.audio-chunk-ready}",
            groupId = "ai-stt-worker"
    )
    public void onMessage(String message) {
        try {
            AudioChunkReadyEvent event = objectMapper.readValue(message, AudioChunkReadyEvent.class);
            sttWorker.handleAudioChunk(event);
        } catch (Exception e) {
            log.error("Failed to process audio.chunk.ready event: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
