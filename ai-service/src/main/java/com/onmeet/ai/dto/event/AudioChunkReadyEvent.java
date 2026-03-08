package com.onmeet.ai.dto.event;

import lombok.*;
import java.time.Instant;

@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class AudioChunkReadyEvent {
    private Long roomId;
    private String participantIdentity;

    private int segmentIndex;

    private String s3Path;

    private Instant startTime;
    private Instant endTime;

    private Instant timestamp;
}
