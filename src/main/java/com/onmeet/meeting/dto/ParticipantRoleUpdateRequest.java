package com.onmeet.meeting.dto;

import com.onmeet.meeting.entity.ParticipantRole;
import jakarta.validation.constraints.NotNull;

public record ParticipantRoleUpdateRequest(
    @NotNull ParticipantRole role
) {
}
