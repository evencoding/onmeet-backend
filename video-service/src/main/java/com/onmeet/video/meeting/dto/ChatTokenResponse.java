package com.onmeet.video.meeting.dto;

public record ChatTokenResponse(
    String token,
    String livekitUrl,
    String roomName,
    Long roomId
) {
}
