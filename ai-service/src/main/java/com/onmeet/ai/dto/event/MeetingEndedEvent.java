package com.onmeet.ai.dto.event;

import lombok.*;

@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class MeetingEndedEvent {
    private String meetingId;
    private long endedAtEpochMs;
}
