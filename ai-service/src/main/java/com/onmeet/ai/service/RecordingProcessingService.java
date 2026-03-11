package com.onmeet.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.dto.event.MinutesGeneratedEvent;
import com.onmeet.ai.dto.event.RecordingStoredEvent;
import com.onmeet.ai.entity.Minutes;
import com.onmeet.ai.messaging.producer.MinutesEventsProducer;
import com.onmeet.ai.messaging.producer.NotificationEventPublisher;
import com.onmeet.ai.pipeline.audio.AudioDecoder;
import com.onmeet.ai.pipeline.nlp.SummarizerClient;
import com.onmeet.ai.pipeline.storage.StorageClient;
import com.onmeet.ai.pipeline.storage.StorageKeyFactory;
import com.onmeet.ai.pipeline.stt.SttClient;
import com.onmeet.ai.pipeline.transcript.TranscriptDocument;
import com.onmeet.ai.pipeline.transcript.TranscriptRenderer;
import com.onmeet.ai.pipeline.vad.VadClient;
import com.onmeet.ai.pipeline.vad.VadClient.SpeechSegment;
import com.onmeet.ai.repository.MinutesRepository;
import com.onmeet.common.dto.NotificationRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RecordingProcessingService {

    private static final Logger log = LoggerFactory.getLogger(RecordingProcessingService.class);

    private final StorageClient storageClient;
    private final AudioDecoder audioDecoder;
    private final VadClient vadClient;
    private final SttClient sttClient;
    private final SummarizerClient summarizerClient;
    private final TranscriptRenderer renderer;
    private final MinutesRepository minutesRepository;
    private final MinutesEventsProducer minutesEventsProducer;
    private final NotificationEventPublisher notificationEventPublisher;
    private final ObjectMapper om;

    public RecordingProcessingService(
            StorageClient storageClient,
            AudioDecoder audioDecoder,
            VadClient vadClient,
            SttClient sttClient,
            SummarizerClient summarizerClient,
            TranscriptRenderer renderer,
            MinutesRepository minutesRepository,
            MinutesEventsProducer minutesEventsProducer,
            NotificationEventPublisher notificationEventPublisher,
            ObjectMapper om
    ) {
        this.storageClient = storageClient;
        this.audioDecoder = audioDecoder;
        this.vadClient = vadClient;
        this.sttClient = sttClient;
        this.summarizerClient = summarizerClient;
        this.renderer = renderer;
        this.minutesRepository = minutesRepository;
        this.minutesEventsProducer = minutesEventsProducer;
        this.notificationEventPublisher = notificationEventPublisher;
        this.om = om;
    }

    public void processRecording(RecordingStoredEvent event) {
        log.info("Processing recording: roomId={}, recordingId={}, s3Path={}",
                event.getRoomId(), event.getRecordingId(), event.getS3Path());

        // 1. S3에서 녹음 파일 다운로드
        byte[] audioBytes = storageClient.readBytes(event.getS3Path());
        if (audioBytes == null || audioBytes.length == 0) {
            log.error("Empty audio file: roomId={}, s3Path={}", event.getRoomId(), event.getS3Path());
            return;
        }

        // 2. 오디오 디코딩 (OGG → PCM 16kHz mono)
        float[] pcmSamples;
        try {
            pcmSamples = audioDecoder.decode(audioBytes);
        } catch (IOException e) {
            log.error("Failed to decode recording audio: roomId={}, recordingId={}",
                    event.getRoomId(), event.getRecordingId(), e);
            return;
        }

        // 3. VAD로 발화 구간 감지
        List<SpeechSegment> segments = vadClient.detectSpeech(pcmSamples, 16000);
        if (segments.isEmpty()) {
            log.warn("No speech detected in recording: roomId={}, recordingId={}",
                    event.getRoomId(), event.getRecordingId());
            return;
        }

        log.info("VAD detected {} speech segments in recording: roomId={}, recordingId={}",
                segments.size(), event.getRoomId(), event.getRecordingId());

        // 4. 각 발화 구간별 STT → 트랜스크립트 이벤트 생성
        List<TranscriptDocument.Event> transcriptEvents = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            SpeechSegment seg = segments.get(i);
            byte[] segmentAudio = audioDecoder.extractAndEncode(pcmSamples, seg);
            if (segmentAudio.length == 0) continue;

            String text = sttClient.transcribe(segmentAudio, "seg-" + i + ".wav", "audio/wav").trim();
            if (text.isBlank()) continue;

            transcriptEvents.add(TranscriptDocument.Event.builder()
                    .id(UUID.randomUUID().toString())
                    .type("VOICE")
                    .actorId(event.getParticipantIdentity())
                    .timestamp(Instant.now())
                    .seq((long) i)
                    .text(text)
                    .segmentStartMs(seg.startMs())
                    .segmentEndMs(seg.endMs())
                    .build());
        }

        if (transcriptEvents.isEmpty()) {
            log.warn("No text extracted from recording: roomId={}, recordingId={}",
                    event.getRoomId(), event.getRecordingId());
            return;
        }

        // 5. 트랜스크립트 문서 생성 및 S3 저장
        String transcriptId = "rec-" + event.getRecordingId() + "-" + UUID.randomUUID().toString().substring(0, 8);
        TranscriptDocument doc = TranscriptDocument.builder()
                .roomId(event.getRoomId())
                .transcriptId(transcriptId)
                .version(1)
                .events(transcriptEvents)
                .build();

        String transcriptS3Key = StorageKeyFactory.transcriptKey(event.getRoomId(), transcriptId);
        try {
            String transcriptJson = om.writeValueAsString(doc);
            storageClient.writeText(transcriptS3Key, transcriptJson, "application/json");
        } catch (Exception e) {
            log.error("Failed to save transcript: roomId={}", event.getRoomId(), e);
            return;
        }

        // 6. 트랜스크립트 → 평문 변환 → Claude 요약
        String plainText = renderer.toPlainText(doc);
        if (plainText == null || plainText.isBlank()) {
            log.error("Empty transcript plain text: roomId={}", event.getRoomId());
            return;
        }

        String summaryJson = summarizerClient.summarize(plainText, "ko", "default", "claude-sonnet");

        // 7. 요약 결과 S3 저장
        String summaryS3Key = StorageKeyFactory.summaryKey(event.getRoomId(), transcriptId);
        storageClient.writeText(summaryS3Key, summaryJson, "application/json");

        // 8. Minutes 엔티티 저장
        upsertMinutes(event.getRoomId(), transcriptId, transcriptS3Key, summaryS3Key, summaryJson);

        // 9. 완료 이벤트 발행
        minutesEventsProducer.publish(MinutesGeneratedEvent.builder()
                .roomId(event.getRoomId())
                .transcriptId(transcriptId)
                .transcriptS3Key(transcriptS3Key)
                .generatedAt(Instant.now())
                .build());

        log.info("Recording processing completed: roomId={}, recordingId={}, transcriptId={}",
                event.getRoomId(), event.getRecordingId(), transcriptId);
    }

    @Transactional
    protected void upsertMinutes(Long roomId, String transcriptId, String transcriptS3Key,
                                  String summaryS3Key, String summaryJson) {
        Minutes m = minutesRepository.findByRoomId(roomId).orElse(null);
        if (m == null) {
            minutesRepository.save(
                    Minutes.createGenerated(roomId, transcriptId, transcriptS3Key, summaryS3Key, summaryJson)
            );
        } else {
            m.applyGenerated(transcriptId, transcriptS3Key, summaryS3Key, summaryJson);
            minutesRepository.save(m);
        }
    }
}
