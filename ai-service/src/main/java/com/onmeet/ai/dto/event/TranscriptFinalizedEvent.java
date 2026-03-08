package com.onmeet.ai.dto.event;

import lombok.*;
import java.time.Instant;

@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class TranscriptFinalizedEvent {
    private Long roomId;
    private String transcriptId;
    private String transcriptS3Key;
    private int version;
    private Instant finalizedAt;
}
