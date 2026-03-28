package com.onmeet.ai.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.common.dto.event.AudioChunkReadyEvent;
import com.onmeet.common.dto.event.SttChunkCompletedEvent;
import com.onmeet.ai.service.SttWorkerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AudioChunkConsumer {

    private static final Logger log = LoggerFactory.getLogger(AudioChunkConsumer.class);

    private final SttWorkerService sttWorker;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String sttCompletedTopic;

    public AudioChunkConsumer(SttWorkerService sttWorker, ObjectMapper objectMapper,
                              KafkaTemplate<String, Object> kafkaTemplate,
                              @Value("${app.kafka.topics.stt-chunk-completed}") String sttCompletedTopic) {
        this.sttWorker = sttWorker;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.sttCompletedTopic = sttCompletedTopic;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.audio-chunk-ready}",
            groupId = "ai-stt-worker"
    )
    public void onMessage(String message) {
        try {
            AudioChunkReadyEvent event = objectMapper.readValue(message, AudioChunkReadyEvent.class);
            sttWorker.handleAudioChunk(event);

            SttChunkCompletedEvent completed = SttChunkCompletedEvent.builder()
                    .roomId(event.getRoomId())
                    .segmentIndex(event.getSegmentIndex())
                    .participantId(event.getParticipantId())
                    .build();
            kafkaTemplate.send(sttCompletedTopic, String.valueOf(event.getRoomId()), completed);
            log.info("STT chunk completed: roomId={}, segmentIndex={}", event.getRoomId(), event.getSegmentIndex());
        } catch (Exception e) {
            log.error("Failed to process audio.chunk.ready event: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
