package com.onmeet.video.meeting.dto;

import java.time.Instant;

public record ScreenShareResponse(
    Long participantId,
    Long userId,
    Instant startedAt
) {
}
