package com.onmeet.video.meeting.dto;

import com.onmeet.video.meeting.entity.DeviceType;

public record RoomJoinRequest(
    String password,
    DeviceType deviceType
) {
}
