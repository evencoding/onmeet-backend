package com.onmeet.video.meeting.dto.room;

import com.onmeet.video.meeting.entity.participant.DeviceType;

public record RoomJoinRequest(
    String password,
    DeviceType deviceType
) {
}
