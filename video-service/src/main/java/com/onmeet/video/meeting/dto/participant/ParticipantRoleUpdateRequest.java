package com.onmeet.video.meeting.dto.participant;

import com.onmeet.video.meeting.entity.participant.ParticipantRole;
import jakarta.validation.constraints.NotNull;

public record ParticipantRoleUpdateRequest(
    @NotNull ParticipantRole role
) {
}
