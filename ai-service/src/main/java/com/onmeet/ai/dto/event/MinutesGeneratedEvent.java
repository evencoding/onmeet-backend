package com.onmeet.ai.dto.event;

import lombok.*;

@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class MinutesGeneratedEvent {
    private String meetingId;
    private String transcriptId;
    private String transcriptS3Key;
    private long generatedAtEpochMs;
}
