package com.onmeet.video.meeting.dto.room;

import com.onmeet.video.meeting.entity.room.RoomAccessScope;
import com.onmeet.video.meeting.entity.room.RoomType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public record RoomCreateRequest(
    @NotBlank String title,
    String description,
    RoomType type,
    @Min(2) @Max(100) Integer maxParticipants,
    String password,
    LocalDateTime scheduledAt,
    RoomAccessScope accessScope,
    Long teamId
) {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public Instant scheduledAtAsInstant() {
        return scheduledAt != null ? scheduledAt.atZone(KST).toInstant() : null;
    }
}
