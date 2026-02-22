package com.onmeet.meeting.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BulkInviteRequest(
    @NotEmpty List<Long> inviteeUserIds
) {
}
