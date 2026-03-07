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
            return;
        }

        String segmentId = UUID.randomUUID().toString();

        long seq = ((long) e.getChunkSeq()) * 1_000_000L;

        producer.publish(VoiceSegmentCreatedEvent.builder()
                .roomId(e.getRoomId())
                .segmentId(segmentId)
                .userId(e.getUserId())
                .startMs(e.getChunkStartMs())
                .endMs(e.getChunkEndMs())
                .seq(seq)
                .text(text)
                .timestamp(Instant.now())
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
