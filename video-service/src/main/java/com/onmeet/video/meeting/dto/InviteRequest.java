package com.onmeet.video.meeting.dto;

import jakarta.validation.constraints.NotNull;

public record InviteRequest(
    @NotNull Long inviteeUserId
) {
}
