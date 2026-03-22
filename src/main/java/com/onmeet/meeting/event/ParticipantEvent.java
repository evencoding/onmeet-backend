package com.onmeet.meeting.event;

import java.time.Instant;

public record ParticipantEvent(
    String type,
    Long roomId,
    Long userId,
    Instant timestamp
) {
}
