package com.onmeet.video.meeting.dto.room;

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
