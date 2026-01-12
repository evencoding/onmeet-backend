package com.onmeet.meeting.dto;

import java.time.Instant;

public record MeetingResponse(
    String id,
    Long teamId,
    Long hostUserId,
    String title,
    Instant scheduledAt,
    Instant startedAt,
    Instant endedAt,
    Instant createdAt
) {
}
