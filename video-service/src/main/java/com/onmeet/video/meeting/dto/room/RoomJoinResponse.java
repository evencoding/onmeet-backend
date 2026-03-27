package com.onmeet.video.meeting.dto.room;

import java.util.List;

public record RoomJoinResponse(
    String token,
    String livekitUrl,
    String roomName,
    boolean waitingRoom,
    List<String> warnings
) {
    public RoomJoinResponse(String token, String livekitUrl, String roomName, boolean waitingRoom) {
        this(token, livekitUrl, roomName, waitingRoom, List.of());
    }
}
