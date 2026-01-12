package com.onmeet.minutes.dto;

import com.onmeet.minutes.entity.MinutesStatus;
import jakarta.validation.constraints.NotNull;

public record MinutesCreateRequest(
    @NotNull String meetingId,
    @NotNull MinutesStatus status,
    String summaryText
) {
}
