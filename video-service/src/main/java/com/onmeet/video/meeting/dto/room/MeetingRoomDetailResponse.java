package com.onmeet.video.meeting.dto.room;

import com.onmeet.video.meeting.entity.room.RoomAccessScope;
import com.onmeet.video.meeting.entity.room.RoomStatus;
import com.onmeet.video.meeting.entity.room.RoomType;
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
    RoomAccessScope accessScope,
    Long teamId,
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
