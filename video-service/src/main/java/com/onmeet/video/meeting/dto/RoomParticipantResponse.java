package com.onmeet.video.meeting.dto;

import com.onmeet.video.meeting.entity.DeviceType;
import com.onmeet.video.meeting.entity.ParticipantRole;
import com.onmeet.video.meeting.entity.ParticipantStatus;
import java.time.Instant;

public record RoomParticipantResponse(
    Long id,
    Long roomId,
    Long userId,
    ParticipantRole role,
    ParticipantStatus status,
    Instant joinedAt,
    Instant leftAt,
    Integer durationSeconds,
    DeviceType deviceType
) {
}
