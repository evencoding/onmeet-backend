package com.onmeet.meeting.dto;

import jakarta.validation.constraints.NotNull;

public record InviteRequest(
    @NotNull Long inviteeUserId
) {
}
