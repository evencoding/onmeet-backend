package com.onmeet.ai.dto.event;

import lombok.*;
import java.time.Instant;

@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class AudioChunkReadyEvent {
    private Long roomId;
    private Long userId;
    private String trackId;

    private int chunkSeq;
    private long chunkStartMs;
    private long chunkEndMs;

    private String audioFileKey;
    private String format;

    private Instant timestamp;
}
