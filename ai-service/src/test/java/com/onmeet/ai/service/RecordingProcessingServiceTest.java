package com.onmeet.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.onmeet.ai.dto.event.MinutesGeneratedEvent;
import com.onmeet.ai.dto.event.RecordingStoredEvent;
import com.onmeet.ai.entity.Minutes;
import com.onmeet.ai.messaging.producer.MinutesEventsProducer;
import com.onmeet.ai.messaging.producer.NotificationEventPublisher;
import com.onmeet.ai.pipeline.audio.AudioDecoder;
import com.onmeet.ai.pipeline.nlp.SummarizerClient;
import com.onmeet.ai.pipeline.storage.StorageClient;
import com.onmeet.ai.pipeline.stt.SttClient;
import com.onmeet.ai.pipeline.transcript.TranscriptRenderer;
import com.onmeet.ai.pipeline.vad.VadClient;
import com.onmeet.ai.pipeline.vad.VadClient.SpeechSegment;
import com.onmeet.ai.repository.MinutesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordingProcessingServiceTest {

    @Mock private StorageClient storageClient;
    @Mock private AudioDecoder audioDecoder;
    @Mock private VadClient vadClient;
    @Mock private SttClient sttClient;
    @Mock private SummarizerClient summarizerClient;
    @Mock private TranscriptRenderer renderer;
    @Mock private MinutesRepository minutesRepository;
    @Mock private MinutesEventsProducer minutesEventsProducer;
    @Mock private NotificationEventPublisher notificationEventPublisher;

    private RecordingProcessingService service;

    @BeforeEach
    void setUp() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        service = new RecordingProcessingService(
                storageClient, audioDecoder, vadClient, sttClient,
                summarizerClient, renderer, minutesRepository,
                minutesEventsProducer, notificationEventPublisher, om
        );
    }

    private RecordingStoredEvent createEvent() {
        return RecordingStoredEvent.builder()
                .fileId(1L)
                .roomId(100L)
                .recordingId(10L)
                .s3Path("/recordings/100/user1/audio_track1.ogg")
                .s3Url("https://cdn.example.com/recordings/100/user1/audio_track1.ogg")
                .fileSizeBytes(2048L)
                .durationSeconds(300)
                .participantIdentity("user1")
                .timestamp("2026-03-11T10:00:00Z")
                .build();
    }

    @Test
    @DisplayName("녹음 처리 성공 시 전체 파이프라인이 실행되어야 한다")
    void processRecording_ShouldRunFullPipeline() throws IOException {
        RecordingStoredEvent event = createEvent();
        byte[] audioBytes = new byte[]{1, 2, 3};
        float[] pcmSamples = new float[]{0.1f, 0.2f};
        byte[] wavBytes = new byte[]{4, 5, 6};

        when(storageClient.readBytes(event.getS3Path())).thenReturn(audioBytes);
        when(audioDecoder.decode(audioBytes)).thenReturn(pcmSamples);
        when(vadClient.detectSpeech(pcmSamples, 16000))
                .thenReturn(List.of(new SpeechSegment(0, 1000)));
        when(audioDecoder.extractAndEncode(eq(pcmSamples), any(SpeechSegment.class)))
                .thenReturn(wavBytes);
        when(sttClient.transcribe(eq(wavBytes), anyString(), eq("audio/wav")))
                .thenReturn("안녕하세요 회의를 시작합니다");
        when(renderer.toPlainText(any())).thenReturn("[VOICE] (0~1000) user1: 안녕하세요 회의를 시작합니다");
        when(summarizerClient.summarize(anyString(), eq("ko"), eq("default"), eq("claude-sonnet")))
                .thenReturn("{\"summary\":\"회의 요약 내용\"}");
        when(minutesRepository.findByRoomId(100L)).thenReturn(Optional.empty());

        service.processRecording(event);

        // 1. 트랜스크립트 S3 저장
        verify(storageClient).writeText(contains("transcripts/100/"), anyString(), eq("application/json"));
        // 2. 요약 S3 저장
        verify(storageClient).writeText(contains("minutes/100/"), eq("{\"summary\":\"회의 요약 내용\"}"), eq("application/json"));
        // 3. Minutes 엔티티 저장
        verify(minutesRepository).save(any(Minutes.class));
        // 4. 완료 이벤트 발행
        ArgumentCaptor<MinutesGeneratedEvent> captor = ArgumentCaptor.forClass(MinutesGeneratedEvent.class);
        verify(minutesEventsProducer).publish(captor.capture());
        assertThat(captor.getValue().getRoomId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("빈 오디오 파일이면 처리가 중단되어야 한다")
    void processRecording_ShouldStop_WhenAudioEmpty() {
        RecordingStoredEvent event = createEvent();
        when(storageClient.readBytes(event.getS3Path())).thenReturn(new byte[0]);

        service.processRecording(event);

        verify(audioDecoder, never()).decode(any());
        verify(minutesEventsProducer, never()).publish(any());
    }

    @Test
    @DisplayName("오디오 디코딩 실패 시 처리가 중단되어야 한다")
    void processRecording_ShouldStop_WhenDecodeFails() throws IOException {
        RecordingStoredEvent event = createEvent();
        byte[] audioBytes = new byte[]{1, 2, 3};

        when(storageClient.readBytes(event.getS3Path())).thenReturn(audioBytes);
        when(audioDecoder.decode(audioBytes)).thenThrow(new IOException("Unsupported format"));

        service.processRecording(event);

        verify(vadClient, never()).detectSpeech(any(), anyInt());
        verify(minutesEventsProducer, never()).publish(any());
    }

    @Test
    @DisplayName("VAD가 발화를 감지하지 못하면 처리가 중단되어야 한다")
    void processRecording_ShouldStop_WhenNoSpeechDetected() throws IOException {
        RecordingStoredEvent event = createEvent();
        byte[] audioBytes = new byte[]{1, 2, 3};
        float[] pcmSamples = new float[]{0.1f};

        when(storageClient.readBytes(event.getS3Path())).thenReturn(audioBytes);
        when(audioDecoder.decode(audioBytes)).thenReturn(pcmSamples);
        when(vadClient.detectSpeech(pcmSamples, 16000)).thenReturn(List.of());

        service.processRecording(event);

        verify(sttClient, never()).transcribe(any(), any(), any());
        verify(minutesEventsProducer, never()).publish(any());
    }

    @Test
    @DisplayName("STT 결과가 모두 비어있으면 요약이 생성되지 않아야 한다")
    void processRecording_ShouldStop_WhenAllSttBlank() throws IOException {
        RecordingStoredEvent event = createEvent();
        byte[] audioBytes = new byte[]{1, 2, 3};
        float[] pcmSamples = new float[]{0.1f};
        byte[] wavBytes = new byte[]{4, 5, 6};

        when(storageClient.readBytes(event.getS3Path())).thenReturn(audioBytes);
        when(audioDecoder.decode(audioBytes)).thenReturn(pcmSamples);
        when(vadClient.detectSpeech(pcmSamples, 16000))
                .thenReturn(List.of(new SpeechSegment(0, 500)));
        when(audioDecoder.extractAndEncode(eq(pcmSamples), any(SpeechSegment.class)))
                .thenReturn(wavBytes);
        when(sttClient.transcribe(eq(wavBytes), anyString(), eq("audio/wav")))
                .thenReturn("   ");

        service.processRecording(event);

        verify(summarizerClient, never()).summarize(any(), any(), any(), any());
        verify(minutesEventsProducer, never()).publish(any());
    }

    @Test
    @DisplayName("여러 발화 구간이 감지되면 모두 STT 처리 후 하나의 트랜스크립트로 합쳐져야 한다")
    void processRecording_ShouldCombineMultipleSegments() throws IOException {
        RecordingStoredEvent event = createEvent();
        byte[] audioBytes = new byte[]{1, 2, 3};
        float[] pcmSamples = new float[]{0.1f, 0.2f, 0.3f};
        byte[] wavBytes = new byte[]{4, 5, 6};

        when(storageClient.readBytes(event.getS3Path())).thenReturn(audioBytes);
        when(audioDecoder.decode(audioBytes)).thenReturn(pcmSamples);
        when(vadClient.detectSpeech(pcmSamples, 16000))
                .thenReturn(List.of(new SpeechSegment(0, 500), new SpeechSegment(1000, 1500)));
        when(audioDecoder.extractAndEncode(eq(pcmSamples), any(SpeechSegment.class)))
                .thenReturn(wavBytes);
        when(sttClient.transcribe(eq(wavBytes), anyString(), eq("audio/wav")))
                .thenReturn("첫번째 발화")
                .thenReturn("두번째 발화");
        when(renderer.toPlainText(any())).thenReturn("combined text");
        when(summarizerClient.summarize(anyString(), eq("ko"), eq("default"), eq("claude-sonnet")))
                .thenReturn("{\"summary\":\"요약\"}");
        when(minutesRepository.findByRoomId(100L)).thenReturn(Optional.empty());

        service.processRecording(event);

        verify(sttClient, times(2)).transcribe(any(), anyString(), any());
        verify(summarizerClient).summarize(eq("combined text"), eq("ko"), eq("default"), eq("claude-sonnet"));
        verify(minutesEventsProducer).publish(any());
    }
}
