package com.onmeet.video.meeting.dto.room;

import java.time.Instant;

public record TimelineEntry(
    String eventType,
    Long userId,
    String description,
    Instant timestamp
) {
}
