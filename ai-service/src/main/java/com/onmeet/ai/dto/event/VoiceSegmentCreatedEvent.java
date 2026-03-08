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

    private String participantIdentity;

    private long segmentStartMs;
    private long segmentEndMs;

    private long seq;

    private String text;

    private Instant timestamp;
}
