package com.onmeet.video.meeting.dto.invitation;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BulkInviteRequest(
    @NotEmpty List<Long> inviteeUserIds
) {
}
