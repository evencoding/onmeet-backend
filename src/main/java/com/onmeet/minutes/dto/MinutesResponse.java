package com.onmeet.minutes.dto;

import com.onmeet.minutes.entity.MinutesStatus;
import java.time.Instant;

public record MinutesResponse(
    Long id,
    String meetingId,
    MinutesStatus status,
    String summaryText,
    Instant createdAt,
    Instant updatedAt
) {
}
