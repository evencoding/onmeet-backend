package com.onmeet.video.meeting.dto;

import com.onmeet.video.meeting.entity.RoomStatus;
import java.time.Instant;

public record RoomStatsResponse(
    Long roomId,
    String title,
    RoomStatus status,
    int totalParticipants,
    int currentParticipants,
    Integer durationSeconds,
    int totalRecordings,
    Instant startedAt,
    Instant endedAt
) {
}
