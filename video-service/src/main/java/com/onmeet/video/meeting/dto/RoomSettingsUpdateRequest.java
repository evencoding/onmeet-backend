package com.onmeet.video.meeting.dto;

public record RoomSettingsUpdateRequest(
    Boolean videoEnabled,
    Boolean audioEnabled,
    Boolean screenShareAllowed,
    Boolean chatEnabled,
    Boolean recordingEnabled,
    Boolean waitingRoom,
    Boolean autoMuteOnJoin
) {
}
