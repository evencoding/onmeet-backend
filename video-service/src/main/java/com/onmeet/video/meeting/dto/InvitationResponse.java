package com.onmeet.video.meeting.dto;

import com.onmeet.video.meeting.entity.InvitationStatus;
import java.time.Instant;

public record InvitationResponse(
    Long id,
    Long roomId,
    Long inviterUserId,
    Long inviteeUserId,
    InvitationStatus status,
    Instant createdAt
) {
}
