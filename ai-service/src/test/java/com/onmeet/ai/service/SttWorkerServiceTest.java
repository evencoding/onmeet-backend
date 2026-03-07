package com.onmeet.ai.service;

import com.onmeet.ai.dto.event.AudioChunkReadyEvent;
import com.onmeet.ai.dto.event.VoiceSegmentCreatedEvent;
import com.onmeet.ai.messaging.producer.VoiceSegmentProducer;
import com.onmeet.ai.pipeline.storage.StorageClient;
import com.onmeet.ai.pipeline.stt.SttClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SttWorkerServiceTest {

    @Mock
    private StorageClient storageClient;

    @Mock
    private SttClient sttClient;

    @Mock
    private VoiceSegmentProducer producer;

    @InjectMocks
    private SttWorkerService sttWorkerService;

    private AudioChunkReadyEvent sampleEvent;

    @BeforeEach
    void setUp() {
        sampleEvent = AudioChunkReadyEvent.builder()
                .meetingId("test-meeting-id")
                .participantId("test-participant-id")
                .audioFileKey("s3/audio/chunk-1.webm")
                .format("webm")
                .chunkSeq(1)
                .chunkStartMs(0L)
                .chunkEndMs(1000L)
                .build();
    }

    @Test
    @DisplayName("유효한 오디오 청크를 처리하고 VoiceSegmentEvent를 발행해야 한다")
    void handleAudioChunk_ShouldPublishEvent_WhenTranscriptionIsValid() {
        // Given
        byte[] mockAudioData = new byte[] { 1, 2, 3 };
        when(storageClient.readBytes(sampleEvent.getAudioFileKey())).thenReturn(mockAudioData);
        when(sttClient.transcribe(eq(mockAudioData), anyString(), eq("audio/webm"))).thenReturn("Hello world");

        // When
        sttWorkerService.handleAudioChunk(sampleEvent);

        // Then
        ArgumentCaptor<VoiceSegmentCreatedEvent> captor = ArgumentCaptor.forClass(VoiceSegmentCreatedEvent.class);
        verify(producer, times(1)).publish(captor.capture());

        VoiceSegmentCreatedEvent capturedEvent = captor.getValue();
        assertThat(capturedEvent.getMeetingId()).isEqualTo(sampleEvent.getMeetingId());
        assertThat(capturedEvent.getText()).isEqualTo("Hello world");
        assertThat(capturedEvent.getParticipantId()).isEqualTo(sampleEvent.getParticipantId());
        assertThat(capturedEvent.getStartMs()).isEqualTo(sampleEvent.getChunkStartMs());
        assertThat(capturedEvent.getEndMs()).isEqualTo(sampleEvent.getChunkEndMs());
    }

    @Test
    @DisplayName("STT 결과가 비어있으면 이벤트를 발행하지 않아야 한다")
    void handleAudioChunk_ShouldNotPublishEvent_WhenTranscriptionIsBlank() {
        // Given
        byte[] mockAudioData = new byte[] { 1, 2, 3 };
        when(storageClient.readBytes(sampleEvent.getAudioFileKey())).thenReturn(mockAudioData);
        when(sttClient.transcribe(eq(mockAudioData), anyString(), eq("audio/webm"))).thenReturn("   ");

        // When
        sttWorkerService.handleAudioChunk(sampleEvent);

        // Then
        verify(producer, never()).publish(any());
    }

    @Test
    @DisplayName("포맷이 null일 경우 기본 MIME 타입을 사용해야 한다")
    void handleAudioChunk_ShouldUseDefaultMimeType_WhenFormatIsNull() {
        // Given
        AudioChunkReadyEvent nullFormatEvent = AudioChunkReadyEvent.builder()
                .meetingId("meeting-id")
                .audioFileKey("key")
                .format(null)
                .chunkSeq(1)
                .build();

        byte[] mockAudioData = new byte[] { 1 };
        when(storageClient.readBytes("key")).thenReturn(mockAudioData);
        when(sttClient.transcribe(eq(mockAudioData), anyString(), eq("application/octet-stream"))).thenReturn("text");

        // When
        sttWorkerService.handleAudioChunk(nullFormatEvent);

        // Then
        verify(sttClient).transcribe(any(), any(), eq("application/octet-stream"));
        verify(producer).publish(any());
    }
}
