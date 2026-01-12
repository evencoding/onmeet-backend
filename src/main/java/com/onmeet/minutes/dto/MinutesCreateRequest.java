package com.onmeet.minutes.dto;

import com.onmeet.minutes.entity.MinutesStatus;
import jakarta.validation.constraints.NotNull;

public record MinutesCreateRequest(
    @NotNull Long meetingId,
    @NotNull MinutesStatus status,
    String summaryText
) {
}
