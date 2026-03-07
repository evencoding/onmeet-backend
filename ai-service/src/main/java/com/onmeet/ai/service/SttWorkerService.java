package com.onmeet.ai.service;

import com.onmeet.ai.dto.event.AudioChunkReadyEvent;
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
        byte[] audioBytes = storageClient.readBytes(e.getS3Path());

        // 1. OGG → PCM 16kHz mono float[]
        float[] pcmSamples;
        try {
            pcmSamples = audioDecoder.decode(audioBytes);
        } catch (IOException ex) {
            log.error("Failed to decode audio: roomId={}, segmentIndex={}",
                    e.getRoomId(), e.getSegmentIndex(), ex);
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

        // 청크 시작 시간 (video-service에서 수신한 startTime 기준)
        long chunkStartMs = e.getStartTime() != null ? e.getStartTime().toEpochMilli() : 0L;

        // 3. 각 발화 구간별로 개별 STT 호출
        for (int i = 0; i < segments.size(); i++) {
            SpeechSegment seg = segments.get(i);

            // 해당 발화 구간만 WAV로 인코딩
            byte[] segmentAudio = audioDecoder.extractAndEncode(pcmSamples, seg);
            if (segmentAudio.length == 0) {
                continue;
            }

            // 개별 STT 호출
            String text = sttClient.transcribe(segmentAudio, "seg-" + i + ".wav", "audio/wav").trim();
            if (text.isBlank()) {
                continue;
            }

            // 발화 구간의 실제 시간 계산 (청크 시작 + 구간 오프셋)
            long segStartMs = chunkStartMs + seg.startMs();
            long segEndMs = chunkStartMs + seg.endMs();
            long seq = ((long) e.getSegmentIndex()) * 1_000_000L + i;

            producer.publish(VoiceSegmentCreatedEvent.builder()
                    .roomId(e.getRoomId())
                    .segmentId(UUID.randomUUID().toString())
                    .participantIdentity(e.getParticipantIdentity())
                    .segmentStartMs(segStartMs)
                    .segmentEndMs(segEndMs)
                    .seq(seq)
                    .text(text)
                    .timestamp(Instant.now())
                    .build());

            log.debug("Published voice segment: roomId={}, participant={}, segmentStartMs={}, segmentEndMs={}",
                    e.getRoomId(), e.getParticipantIdentity(), segStartMs, segEndMs);
        }
    }
}
