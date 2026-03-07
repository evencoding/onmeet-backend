package com.onmeet.video.meeting.dto;

import com.onmeet.video.meeting.entity.RoomAccessScope;
import com.onmeet.video.meeting.entity.RoomType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record RoomCreateRequest(
    @NotBlank String title,
    String description,
    RoomType type,
    @Min(2) @Max(100) Integer maxParticipants,
    String password,
    Instant scheduledAt,
    RoomAccessScope accessScope,
    Long teamId
) {
}
