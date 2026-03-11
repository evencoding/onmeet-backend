package com.onmeet.ai.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.onmeet.ai.dto.event.RecordingStoredEvent;
import com.onmeet.ai.service.RecordingProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordingStoredConsumerTest {

    @Mock private RecordingProcessingService recordingProcessingService;
    @Mock private Acknowledgment ack;

    private RecordingStoredConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        consumer = new RecordingStoredConsumer(om, recordingProcessingService);
    }

    @Test
    @DisplayName("유효한 메시지 수신 시 processRecording이 호출되고 ack되어야 한다")
    void onMessage_ShouldProcessAndAck_WhenValidMessage() {
        String json = """
                {
                    "fileId": 1,
                    "roomId": 100,
                    "recordingId": 10,
                    "s3Path": "/recordings/100/user1/audio.ogg",
                    "s3Url": "https://cdn.example.com/recordings/100/user1/audio.ogg",
                    "fileSizeBytes": 2048,
                    "durationSeconds": 300,
                    "participantIdentity": "user1",
                    "timestamp": "2026-03-11T10:00:00Z"
                }
                """;

        consumer.onMessage(json, ack);

        ArgumentCaptor<RecordingStoredEvent> captor = ArgumentCaptor.forClass(RecordingStoredEvent.class);
        verify(recordingProcessingService).processRecording(captor.capture());
        verify(ack).acknowledge();

        RecordingStoredEvent event = captor.getValue();
        assertThat(event.getRoomId()).isEqualTo(100L);
        assertThat(event.getRecordingId()).isEqualTo(10L);
        assertThat(event.getS3Path()).isEqualTo("/recordings/100/user1/audio.ogg");
        assertThat(event.getParticipantIdentity()).isEqualTo("user1");
    }

    @Test
    @DisplayName("잘못된 JSON 메시지 수신 시 RuntimeException이 발생해야 한다")
    void onMessage_ShouldThrow_WhenInvalidJson() {
        String invalidJson = "not-a-json";

        assertThatThrownBy(() -> consumer.onMessage(invalidJson, ack))
                .isInstanceOf(RuntimeException.class);

        verify(recordingProcessingService, never()).processRecording(any());
        verify(ack, never()).acknowledge();
    }

    @Test
    @DisplayName("processRecording 실패 시 RuntimeException이 전파되어야 한다")
    void onMessage_ShouldThrow_WhenProcessingFails() {
        String json = """
                {
                    "fileId": 1,
                    "roomId": 100,
                    "recordingId": 10,
                    "s3Path": "/recordings/100/user1/audio.ogg"
                }
                """;

        doThrow(new RuntimeException("Processing failed"))
                .when(recordingProcessingService).processRecording(any());

        assertThatThrownBy(() -> consumer.onMessage(json, ack))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Processing failed");

        verify(ack, never()).acknowledge();
    }
}
