package com.onmeet.video.meeting.dto.invitation;

import jakarta.validation.constraints.NotNull;

public record InviteRequest(
    @NotNull Long inviteeUserId
) {
}
