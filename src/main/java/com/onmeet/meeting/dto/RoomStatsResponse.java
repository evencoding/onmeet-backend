package com.onmeet.meeting.dto;

import com.onmeet.meeting.entity.RoomStatus;
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
