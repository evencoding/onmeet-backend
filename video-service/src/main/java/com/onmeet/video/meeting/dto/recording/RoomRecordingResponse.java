package com.onmeet.video.meeting.dto.recording;

import com.onmeet.video.meeting.entity.recording.RecordingStatus;
import com.onmeet.video.meeting.entity.recording.RecordingType;
import java.time.Instant;

public record RoomRecordingResponse(
    Long id,
    Long roomId,
    String egressId,
    RecordingType type,
    RecordingStatus status,
    String s3Path,
    Long fileSizeBytes,
    Integer durationSeconds,
    Integer segmentIndex,
    String participantIdentity,
    String trackSid,
    Instant startedAt,
    Instant endedAt,
    Instant createdAt
) {
}
