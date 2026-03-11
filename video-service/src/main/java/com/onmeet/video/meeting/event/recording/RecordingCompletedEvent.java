package com.onmeet.video.meeting.event.recording;

import java.time.Instant;

public record RecordingCompletedEvent(
    Long roomId,
    Long recordingId,
    String egressId,
    String s3Path,
    Long fileSizeBytes,
    Integer durationSeconds,
    String participantIdentity,
    String trackSid,
    String recordingType,
    Instant startedAt,
    Instant endedAt,
    Instant timestamp
) {
}
