package com.onmeet.video.meeting.dto;

public record RoomJoinResponse(
    String token,
    String livekitUrl,
    String roomName,
    boolean waitingRoom
) {
}
