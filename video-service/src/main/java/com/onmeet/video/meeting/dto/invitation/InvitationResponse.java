package com.onmeet.video.meeting.dto.invitation;

import com.onmeet.video.meeting.entity.invitation.InvitationStatus;
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
