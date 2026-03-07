package com.onmeet.video.meeting.event.screenshare;

import java.time.Instant;

public record ScreenShareEvent(
    String type,
    Long roomId,
    Long userId,
    Instant timestamp
) {
}
