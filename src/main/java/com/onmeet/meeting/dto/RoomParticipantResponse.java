package com.onmeet.meeting.dto;

import com.onmeet.meeting.entity.DeviceType;
import com.onmeet.meeting.entity.ParticipantRole;
import com.onmeet.meeting.entity.ParticipantStatus;
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
