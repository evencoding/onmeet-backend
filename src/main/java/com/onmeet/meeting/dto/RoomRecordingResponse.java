package com.onmeet.meeting.dto;

import com.onmeet.meeting.entity.RecordingStatus;
import com.onmeet.meeting.entity.RecordingType;
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
    Instant startedAt,
    Instant endedAt,
    Instant createdAt
) {
}
