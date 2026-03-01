package com.onmeet.video.meeting.dto;

import com.onmeet.video.meeting.entity.RoomAccessScope;
import com.onmeet.video.meeting.entity.RoomStatus;
import com.onmeet.video.meeting.entity.RoomType;
import java.time.Instant;

public record MeetingRoomResponse(
    Long id,
    String roomCode,
    String title,
    String description,
    Long hostUserId,
    RoomStatus status,
    RoomType type,
    RoomAccessScope accessScope,
    Long teamId,
    int maxParticipants,
    boolean locked,
    Instant scheduledAt,
    Instant startedAt,
    Instant endedAt,
    Integer durationSeconds,
    Instant createdAt
) {
}
