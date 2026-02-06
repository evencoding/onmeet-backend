package com.onmeet.meeting.dto;

import java.time.Instant;

public record TimelineEntry(
    String eventType,
    Long userId,
    String description,
    Instant timestamp
) {
}
