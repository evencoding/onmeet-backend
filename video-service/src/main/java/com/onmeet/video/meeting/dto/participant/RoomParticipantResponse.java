package com.onmeet.video.meeting.dto.participant;

import com.onmeet.video.meeting.entity.participant.DeviceType;
import com.onmeet.video.meeting.entity.participant.ParticipantRole;
import com.onmeet.video.meeting.entity.participant.ParticipantStatus;
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
