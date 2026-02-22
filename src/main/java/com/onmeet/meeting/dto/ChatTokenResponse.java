package com.onmeet.meeting.dto;

public record ChatTokenResponse(
    String token,
    String livekitUrl,
    String roomName,
    Long roomId
) {
}
