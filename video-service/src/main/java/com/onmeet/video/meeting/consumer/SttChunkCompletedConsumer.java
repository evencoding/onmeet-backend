package com.onmeet.video.meeting.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.common.dto.event.SttChunkCompletedEvent;
import com.onmeet.video.meeting.service.recording.RoomRecordingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true")
public class SttChunkCompletedConsumer {

    private static final Logger log = LoggerFactory.getLogger(SttChunkCompletedConsumer.class);

    private final RoomRecordingService recordingService;
    private final ObjectMapper objectMapper;

    public SttChunkCompletedConsumer(RoomRecordingService recordingService, ObjectMapper objectMapper) {
        this.recordingService = recordingService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.stt-chunk-completed:stt.chunk.completed}",
            groupId = "video-stt-completion"
    )
    public void onMessage(String message) {
        try {
            SttChunkCompletedEvent event = objectMapper.readValue(message, SttChunkCompletedEvent.class);
            log.info("STT chunk completed: roomId={}, segmentIndex={}, participantId={}",
                    event.getRoomId(), event.getSegmentIndex(), event.getParticipantId());
            recordingService.handleSttChunkCompleted(event.getRoomId(), event.getParticipantId());
        } catch (Exception e) {
            log.error("Failed to process stt.chunk.completed event: {}", e.getMessage(), e);
        }
    }
}
