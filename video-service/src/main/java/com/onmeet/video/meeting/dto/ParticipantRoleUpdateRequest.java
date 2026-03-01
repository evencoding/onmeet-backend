package com.onmeet.video.meeting.dto;

import com.onmeet.video.meeting.entity.ParticipantRole;
import jakarta.validation.constraints.NotNull;

public record ParticipantRoleUpdateRequest(
    @NotNull ParticipantRole role
) {
}
