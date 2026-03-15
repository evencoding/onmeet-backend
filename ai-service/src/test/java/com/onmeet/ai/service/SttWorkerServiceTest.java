package com.onmeet.ai.service;

import com.onmeet.common.dto.event.AudioChunkReadyEvent;
import com.onmeet.ai.dto.event.VoiceSegmentCreatedEvent;
import com.onmeet.ai.messaging.producer.VoiceSegmentProducer;
import com.onmeet.ai.pipeline.audio.AudioDecoder;
import com.onmeet.ai.pipeline.storage.StorageClient;
import com.onmeet.ai.pipeline.stt.SttClient;
import com.onmeet.ai.pipeline.vad.VadClient;
import com.onmeet.ai.pipeline.vad.VadClient.SpeechSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SttWorkerServiceTest {

    @Mock private StorageClient storageClient;
    @Mock private SttClient sttClient;
    @Mock private VadClient vadClient;
    @Mock private AudioDecoder audioDecoder;
    @Mock private VoiceSegmentProducer producer;

    @InjectMocks
    private SttWorkerService sttWorkerService;

    private AudioChunkReadyEvent sampleEvent;
    private float[] mockPcmSamples;

    @BeforeEach
    void setUp() {
        sampleEvent = AudioChunkReadyEvent.builder()
                .roomId(1L)
                .participantIdentity("김철수")
                .s3Path("recordings/1/김철수/audio_TR_xxxx.ogg")
                .segmentIndex(0)
                .startTime(Instant.ofEpochMilli(0))
                .endTime(Instant.ofEpochMilli(600000))
                .timestamp(Instant.now())
                .build();

        mockPcmSamples = new float[]{0.1f, 0.2f, 0.3f};
    }

    @Test
    @DisplayName("VAD가 발화를 감지하면, 각 구간별로 개별 STT 호출 후 이벤트를 발행해야 한다")
    void handleAudioChunk_ShouldPublishPerSegment_WhenSpeechDetected() throws IOException {
        byte[] mockAudioData = new byte[]{1, 2, 3};
        byte[] mockWavData = new byte[]{4, 5, 6};

        when(storageClient.readBytes(sampleEvent.getS3Path())).thenReturn(mockAudioData);
        when(audioDecoder.decode(mockAudioData)).thenReturn(mockPcmSamples);

        SpeechSegment seg1 = new SpeechSegment(1500, 2750);
        SpeechSegment seg2 = new SpeechSegment(5000, 6100);
        when(vadClient.detectSpeech(mockPcmSamples, 16000)).thenReturn(List.of(seg1, seg2));

        when(audioDecoder.extractAndEncode(eq(mockPcmSamples), any(SpeechSegment.class))).thenReturn(mockWavData);
        when(sttClient.transcribe(eq(mockWavData), anyString(), eq("audio/wav")))
                .thenReturn("안녕하세요")
                .thenReturn("감사합니다");

        sttWorkerService.handleAudioChunk(sampleEvent);

        ArgumentCaptor<VoiceSegmentCreatedEvent> captor = ArgumentCaptor.forClass(VoiceSegmentCreatedEvent.class);
        verify(producer, times(2)).publish(captor.capture());

        List<VoiceSegmentCreatedEvent> events = captor.getAllValues();

        assertThat(events.get(0).getRoomId()).isEqualTo(1L);
        assertThat(events.get(0).getParticipantIdentity()).isEqualTo("김철수");
        assertThat(events.get(0).getText()).isEqualTo("안녕하세요");
        assertThat(events.get(0).getSegmentStartMs()).isEqualTo(1500L);
        assertThat(events.get(0).getSegmentEndMs()).isEqualTo(2750L);

        assertThat(events.get(1).getText()).isEqualTo("감사합니다");
        assertThat(events.get(1).getSegmentStartMs()).isEqualTo(5000L);
        assertThat(events.get(1).getSegmentEndMs()).isEqualTo(6100L);
    }

    @Test
    @DisplayName("VAD가 발화를 감지하지 못하면 이벤트를 발행하지 않아야 한다")
    void handleAudioChunk_ShouldNotPublish_WhenNoSpeechDetected() throws IOException {
        byte[] mockAudioData = new byte[]{1, 2, 3};
        when(storageClient.readBytes(sampleEvent.getS3Path())).thenReturn(mockAudioData);
        when(audioDecoder.decode(mockAudioData)).thenReturn(mockPcmSamples);
        when(vadClient.detectSpeech(mockPcmSamples, 16000)).thenReturn(List.of());

        sttWorkerService.handleAudioChunk(sampleEvent);

        verify(sttClient, never()).transcribe(any(), any(), any());
        verify(producer, never()).publish(any());
    }

    @Test
    @DisplayName("STT 결과가 비어있으면 해당 구간의 이벤트를 발행하지 않아야 한다")
    void handleAudioChunk_ShouldSkipBlankTranscription() throws IOException {
        byte[] mockAudioData = new byte[]{1, 2, 3};
        byte[] mockWavData = new byte[]{4, 5, 6};

        when(storageClient.readBytes(sampleEvent.getS3Path())).thenReturn(mockAudioData);
        when(audioDecoder.decode(mockAudioData)).thenReturn(mockPcmSamples);
        when(vadClient.detectSpeech(mockPcmSamples, 16000))
                .thenReturn(List.of(new SpeechSegment(0, 1000)));
        when(audioDecoder.extractAndEncode(eq(mockPcmSamples), any(SpeechSegment.class))).thenReturn(mockWavData);
        when(sttClient.transcribe(eq(mockWavData), anyString(), eq("audio/wav"))).thenReturn("   ");

        sttWorkerService.handleAudioChunk(sampleEvent);

        verify(producer, never()).publish(any());
    }

    @Test
    @DisplayName("오디오 디코딩 실패 시 이벤트를 발행하지 않아야 한다")
    void handleAudioChunk_ShouldNotPublish_WhenDecodeThrowsIOException() throws IOException {
        byte[] mockAudioData = new byte[]{1, 2, 3};
        when(storageClient.readBytes(sampleEvent.getS3Path())).thenReturn(mockAudioData);
        when(audioDecoder.decode(mockAudioData)).thenThrow(new IOException("Unsupported format"));
        when(sttClient.transcribe(any(), any(), any())).thenReturn(null);

        sttWorkerService.handleAudioChunk(sampleEvent);

        verify(vadClient, never()).detectSpeech(any(), anyInt());
        // 디코딩 실패 시 fallback으로 transcribe 호출되지만, null 반환 시 이벤트 미발행
        verify(producer, never()).publish(any());
    }

    @Test
    @DisplayName("segmentIndex가 0이 아닌 경우 시간 오프셋이 startTime 기준으로 계산되어야 한다")
    void handleAudioChunk_ShouldOffsetTimes_BasedOnStartTime() throws IOException {
        AudioChunkReadyEvent secondSegmentEvent = AudioChunkReadyEvent.builder()
                .roomId(1L)
                .participantIdentity("김철수")
                .s3Path("recordings/1/김철수/audio_TR_yyyy.ogg")
                .segmentIndex(1)
                .startTime(Instant.ofEpochMilli(600000))
                .endTime(Instant.ofEpochMilli(1200000))
                .timestamp(Instant.now())
                .build();

        byte[] mockAudioData = new byte[]{1, 2, 3};
        byte[] mockWavData = new byte[]{4, 5, 6};

        when(storageClient.readBytes(secondSegmentEvent.getS3Path())).thenReturn(mockAudioData);
        when(audioDecoder.decode(mockAudioData)).thenReturn(mockPcmSamples);
        when(vadClient.detectSpeech(mockPcmSamples, 16000))
                .thenReturn(List.of(new SpeechSegment(30000, 60000)));
        when(audioDecoder.extractAndEncode(eq(mockPcmSamples), any(SpeechSegment.class))).thenReturn(mockWavData);
        when(sttClient.transcribe(eq(mockWavData), anyString(), eq("audio/wav"))).thenReturn("회의를 시작");

        sttWorkerService.handleAudioChunk(secondSegmentEvent);

        ArgumentCaptor<VoiceSegmentCreatedEvent> captor = ArgumentCaptor.forClass(VoiceSegmentCreatedEvent.class);
        verify(producer).publish(captor.capture());

        VoiceSegmentCreatedEvent event = captor.getValue();
        assertThat(event.getSegmentStartMs()).isEqualTo(630000L);  // 600000 + 30000
        assertThat(event.getSegmentEndMs()).isEqualTo(660000L);    // 600000 + 60000
    }
}
