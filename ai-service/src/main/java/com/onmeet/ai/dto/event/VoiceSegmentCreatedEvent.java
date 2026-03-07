package com.onmeet.ai.dto.event;

import lombok.*;
import java.time.Instant;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceSegmentCreatedEvent {

    private Long roomId;
    private String segmentId;

    private Long userId;
    private String trackId;

    private long startMs;
    private long endMs;

    private long seq;

    private String text;

    private Instant timestamp;
}
