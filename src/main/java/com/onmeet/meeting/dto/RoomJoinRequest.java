package com.onmeet.meeting.dto;

import com.onmeet.meeting.entity.DeviceType;

public record RoomJoinRequest(
    String password,
    DeviceType deviceType
) {
}
