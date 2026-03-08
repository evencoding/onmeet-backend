package com.onmeet.video.meeting.event.recording;

import java.time.Instant;

public record AudioSegmentEvent(
    String type,
    Long roomId,
    String participantIdentity,
    int segmentIndex,
    String s3Path,
    Instant startTime,
    Instant endTime
) {
}
