package com.onmeet.ai.dto.event;

import lombok.*;

@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class TranscriptFinalizedEvent {
    private String meetingId;
    private String transcriptId;
    private String transcriptS3Key;
    private int version;
    private long finalizedAtEpochMs;
}
