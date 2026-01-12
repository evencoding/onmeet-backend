package com.onmeet.meeting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record MeetingCreateRequest(
    @NotNull Long teamId,
    @NotNull Long hostUserId,
    @NotBlank String title,
    @NotNull Instant scheduledAt
) {
}
