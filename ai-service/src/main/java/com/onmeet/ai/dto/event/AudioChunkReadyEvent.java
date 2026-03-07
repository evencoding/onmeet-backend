package com.onmeet.ai.dto.event;

import lombok.*;

@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class AudioChunkReadyEvent {
    private String meetingId;
    private String participantId;
    private String trackId;

    private int chunkSeq;
    private long chunkStartMs;
    private long chunkEndMs;

    // 오디오 위치 (지금은 S3 key로 가정)
    private String audioFileKey; // 예: audio-chunks/{meetingId}/{participantId}/{chunkSeq}.webm
    private String format;       // webm/ogg/wav...

    private long occurredAtEpochMs;
}
