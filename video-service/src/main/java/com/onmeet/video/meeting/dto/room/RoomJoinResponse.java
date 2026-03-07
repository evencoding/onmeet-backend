package com.onmeet.video.meeting.dto.room;

public record RoomJoinResponse(
    String token,
    String livekitUrl,
    String roomName,
    boolean waitingRoom
) {
}
