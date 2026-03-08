package com.onmeet.ai.dto.event;

import lombok.*;
import java.time.Instant;

@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class MeetingEndedEvent {
    private Long roomId;
    private Long hostUserId;
    private Instant endedAt;
}
