package com.onmeet.meeting.dto;

import com.onmeet.meeting.entity.RoomStatus;
import com.onmeet.meeting.entity.RoomType;
import java.time.Instant;
import java.util.List;

public record MeetingRoomDetailResponse(
    Long id,
    String roomCode,
    String title,
    String description,
    Long hostUserId,
    RoomStatus status,
    RoomType type,
    int maxParticipants,
    boolean locked,
    Instant scheduledAt,
    Instant startedAt,
    Instant endedAt,
    Integer durationSeconds,
    int currentParticipantCount,
    RoomSettingsResponse settings,
    List<String> tags,
    Instant createdAt
) {
}
