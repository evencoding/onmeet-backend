package com.onmeet.video.meeting.dto.room;

import com.onmeet.video.meeting.entity.room.RoomAccessScope;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public record RoomScheduleRequest(
    @NotBlank String title,
    String description,
    @NotNull @Future LocalDateTime scheduledAt,
    @Min(2) @Max(100) Integer maxParticipants,
    String password,
    RoomAccessScope accessScope,
    Long teamId
) {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public Instant scheduledAtAsInstant() {
        return scheduledAt.atZone(KST).toInstant();
    }
}
