package com.onmeet.meeting.dto;

public record RoomSettingsResponse(
    Long id,
    Long roomId,
    boolean videoEnabled,
    boolean audioEnabled,
    boolean screenShareAllowed,
    boolean chatEnabled,
    boolean recordingEnabled,
    boolean waitingRoom,
    boolean autoMuteOnJoin
) {
}
