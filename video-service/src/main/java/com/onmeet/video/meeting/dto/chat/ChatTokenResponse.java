package com.onmeet.video.meeting.dto.chat;

public record ChatTokenResponse(
    String token,
    String livekitUrl,
    String roomName,
    Long roomId
) {
}
