package com.onmeet.ai.service;

import com.onmeet.ai.dto.event.AudioChunkReadyEvent;
import com.onmeet.ai.dto.event.VoiceSegmentCreatedEvent;
import com.onmeet.ai.messaging.producer.VoiceSegmentProducer;
import com.onmeet.ai.pipeline.storage.StorageClient;
import com.onmeet.ai.pipeline.stt.SttClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class SttWorkerService {

    private final StorageClient storageClient;
    private final SttClient sttClient;
    private final VoiceSegmentProducer producer;

    public SttWorkerService(StorageClient storageClient, SttClient sttClient, VoiceSegmentProducer producer) {
        this.storageClient = storageClient;
        this.sttClient = sttClient;
        this.producer = producer;
    }

    public void handleAudioChunk(AudioChunkReadyEvent e) {
        byte[] audio = storageClient.readBytes(e.getAudioFileKey());

        String mimeType = guessMimeType(e.getFormat());
        String filename = "chunk-" + e.getChunkSeq() + "." + (e.getFormat() == null ? "bin" : e.getFormat());

        String text = sttClient.transcribe(audio, filename, mimeType).trim();
        if (text.isBlank()) {
            // 빈 텍스트도 segment로 남길지 정책인데, 일단 skip 추천
            return;
        }

        // v1: chunk 전체를 1개 segment로 취급 (나중에 word timestamp 나오면 쪼개면 됨)
        String segmentId = UUID.randomUUID().toString();

        long seq = ((long) e.getChunkSeq()) * 1_000_000L; // tie-breaker 기본값

        producer.publish(VoiceSegmentCreatedEvent.builder()
                .meetingId(e.getMeetingId())
                .segmentId(segmentId)
                .participantId(e.getParticipantId())
                .startMs(e.getChunkStartMs())
                .endMs(e.getChunkEndMs())
                .seq(seq)
                .text(text)
                .occurredAtEpochMs(Instant.now().toEpochMilli())
                .build());
    }

    private String guessMimeType(String format) {
        if (format == null) return "application/octet-stream";
        return switch (format.toLowerCase()) {
            case "webm" -> "audio/webm";
            case "ogg" -> "audio/ogg";
            case "wav" -> "audio/wav";
            case "mp3" -> "audio/mpeg";
            default -> "application/octet-stream";
        };
    }
}
