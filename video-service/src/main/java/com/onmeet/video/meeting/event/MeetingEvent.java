package com.onmeet.video.meeting.event;

import java.time.Instant;

public record MeetingEvent(
    String type,
    Long roomId,
    Long hostUserId,
    int participantCount,
    Instant startedAt,
    Instant endedAt
) {
}
