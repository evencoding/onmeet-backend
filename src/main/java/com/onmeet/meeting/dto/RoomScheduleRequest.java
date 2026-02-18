package com.onmeet.meeting.dto;

import com.onmeet.meeting.entity.RoomAccessScope;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record RoomScheduleRequest(
    @NotBlank String title,
    String description,
    @NotNull @Future Instant scheduledAt,
    @Min(2) @Max(100) Integer maxParticipants,
    String password,
    RoomAccessScope accessScope,
    Long teamId
) {
}
