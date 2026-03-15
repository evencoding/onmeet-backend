package com.onmeet.ai.service;

import com.onmeet.common.dto.event.AudioChunkReadyEvent;
import com.onmeet.ai.dto.event.VoiceSegmentCreatedEvent;
import com.onmeet.ai.messaging.producer.VoiceSegmentProducer;
import com.onmeet.ai.pipeline.audio.AudioDecoder;
import com.onmeet.ai.pipeline.storage.StorageClient;
import com.onmeet.ai.pipeline.stt.SttClient;
import com.onmeet.ai.pipeline.vad.VadClient;
import com.onmeet.ai.pipeline.vad.VadClient.SpeechSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SttWorkerService {

    private static final Logger log = LoggerFactory.getLogger(SttWorkerService.class);


    private final StorageClient storageClient;
    private final SttClient sttClient;
    private final VadClient vadClient;
    private final AudioDecoder audioDecoder;
    private final VoiceSegmentProducer producer;

    public SttWorkerService(StorageClient storageClient, SttClient sttClient,
                            VadClient vadClient, AudioDecoder audioDecoder,
                            VoiceSegmentProducer producer) {
        this.storageClient = storageClient;
        this.sttClient = sttClient;
        this.vadClient = vadClient;
        this.audioDecoder = audioDecoder;
        this.producer = producer;
    }

    public void handleAudioChunk(AudioChunkReadyEvent e) {
        byte[] audioBytes;
        if (e.getFileId() != null) {
            log.debug("Found fileId in event, using FileServerStorageClient: roomId={}, fileId={}", e.getRoomId(), e.getFileId());
            audioBytes = storageClient.readBytes(e.getFileId());
        } else {
            log.debug("fileId not found, falling back to S3 path: roomId={}, s3Path={}", e.getRoomId(), e.getS3Path());
            audioBytes = storageClient.readBytes(e.getS3Path());
        }

        float[] pcmSamples = null;
        try {
            pcmSamples = audioDecoder.decode(audioBytes);
        } catch (IOException ex) {
            log.warn("Failed to decode audio for VAD, falling back to full chunk STT: roomId={}, segmentIndex={}",
                    e.getRoomId(), e.getSegmentIndex());
        }

        // 청크 시작 시간 (video-service에서 수신한 startTime 기준)
        long chunkStartMs = e.getStartTime() != null ? e.getStartTime().toEpochMilli() : 0L;
        long chunkEndMs = e.getEndTime() != null ? e.getEndTime().toEpochMilli() : chunkStartMs + 600000L; // 대략 10분

        // IF fallback triggered (pcmSamples == null) OR VAD disabled
        if (pcmSamples == null) {
            String text = sttClient.transcribe(audioBytes, "chunk-" + e.getSegmentIndex() + e.getS3Path().substring(e.getS3Path().lastIndexOf('.')), "audio/mp4").trim();
            if (!text.isBlank()) {
                long seq = ((long) e.getSegmentIndex()) * 1_000_000L;
                publishVoiceSegment(e, chunkStartMs, chunkEndMs, seq, text);
            }
            return;
        }

        // 2. VAD로 발화 구간 감지 (여러 개 반환 가능)
        List<SpeechSegment> segments = vadClient.detectSpeech(pcmSamples, 16000);
        if (segments.isEmpty()) {
            log.debug("No speech detected in chunk: roomId={}, segmentIndex={}",
                    e.getRoomId(), e.getSegmentIndex());
            return;
        }

        log.info("VAD detected {} speech segment(s) in chunk: roomId={}, segmentIndex={}",
                segments.size(), e.getRoomId(), e.getSegmentIndex());

        // 청크 시작 시간은 위에서 계산된 chunkStartMs를 사용함

        // 3. 각 발화 구간별로 개별 STT 호출
        for (int i = 0; i < segments.size(); i++) {
            SpeechSegment seg = segments.get(i);
            long durationMs = seg.endMs() - seg.startMs();

            // OpenAI API는 0.1초 미만의 오디오를 거절함
            if (durationMs < 100) {
                log.debug("Skipping too short segment: {}ms", durationMs);
                continue;
            }

            // 해당 발화 구간만 WAV로 인코딩
            byte[] segmentAudio = audioDecoder.extractAndEncode(pcmSamples, seg);
            if (segmentAudio.length == 0) continue;

            // 개별 STT 호출 (파일명에 시간 정보 포함하여 디버깅 용이성 확보)
            String text = sttClient.transcribe(segmentAudio,
                    String.format("seg-%d-%dms.wav", i, durationMs),
                    "audio/wav").trim();
            if (text.isBlank()) {
                continue;
            }

            // 발화 구간의 실제 시간 계산 (청크 시작 + 구간 오프셋)
            long segStartMs = chunkStartMs + seg.startMs();
            long segEndMs = chunkStartMs + seg.endMs();
            long seq = ((long) e.getSegmentIndex()) * 1_000_000L + i;

            publishVoiceSegment(e, segStartMs, segEndMs, seq, text);
        }
    }

    private void publishVoiceSegment(AudioChunkReadyEvent e, long startMs, long endMs, long seq, String text) {
        producer.publish(VoiceSegmentCreatedEvent.builder()
                .roomId(e.getRoomId())
                .segmentId(UUID.randomUUID().toString())
                .participantIdentity(e.getParticipantIdentity())
                .segmentStartMs(startMs)
                .segmentEndMs(endMs)
                .seq(seq)
                .text(text)
                .timestamp(Instant.now())
                .build());

        log.debug("Published voice segment: roomId={}, participant={}, startMs={}, endMs={}",
                e.getRoomId(), e.getParticipantIdentity(), startMs, endMs);
    }
}
