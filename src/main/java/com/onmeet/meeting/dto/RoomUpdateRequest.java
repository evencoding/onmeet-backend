package com.onmeet.meeting.dto;

public record RoomUpdateRequest(
    String title,
    String description,
    Integer maxParticipants
) {
}
