package com.onmeet.video.meeting.dto.chat;

import jakarta.validation.constraints.NotBlank;

public record ChatTokenRequest(
    Long roomId,
    String roomCode,
    @NotBlank String serviceIdentity
) {
}
