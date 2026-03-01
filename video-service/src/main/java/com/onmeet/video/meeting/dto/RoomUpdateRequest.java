package com.onmeet.video.meeting.dto;

public record RoomUpdateRequest(
    String title,
    String description,
    Integer maxParticipants
) {
}
