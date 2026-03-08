package com.onmeet.video.meeting.dto.waiting;

import com.onmeet.video.meeting.dto.participant.RoomParticipantResponse;

public record WaitingRoomEvent(
    String type,
    Long roomId,
    Long userId,
    String token,
    String livekitUrl,
    String roomName,
    RoomParticipantResponse participant
) {

    public static WaitingRoomEvent connect() {
        return new WaitingRoomEvent("CONNECT", null, null, null, null, null, null);
    }

    public static WaitingRoomEvent admitted(Long roomId, Long userId, String token, String livekitUrl, String roomName) {
        return new WaitingRoomEvent("ADMITTED", roomId, userId, token, livekitUrl, roomName, null);
    }

    public static WaitingRoomEvent rejected(Long roomId, Long userId) {
        return new WaitingRoomEvent("REJECTED", roomId, userId, null, null, null, null);
    }

    public static WaitingRoomEvent participantWaiting(Long roomId, Long userId, RoomParticipantResponse participant) {
        return new WaitingRoomEvent("PARTICIPANT_WAITING", roomId, userId, null, null, null, participant);
    }

    public static WaitingRoomEvent participantLeftWaiting(Long roomId, Long userId) {
        return new WaitingRoomEvent("PARTICIPANT_LEFT_WAITING", roomId, userId, null, null, null, null);
    }
}
