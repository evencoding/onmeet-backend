package com.onmeet.minutes.dto;

import com.onmeet.minutes.entity.MinutesStatus;
import java.time.Instant;

public record MinutesResponse(
    String minutesId,
    String meetingId,
    String status,      // MinutesStatus name()
    String summaryText,
    Instant createdAt,
    Instant updatedAt
) {
}
