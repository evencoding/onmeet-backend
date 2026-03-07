package com.onmeet.video.meeting.dto.room;

public record RoomUpdateRequest(
    String title,
    String description,
    Integer maxParticipants
) {
}
