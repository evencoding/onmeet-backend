package com.onmeet.meeting.dto;

import com.onmeet.meeting.entity.InvitationStatus;
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
