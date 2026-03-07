package com.onmeet.video.meeting.dto.room;

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
