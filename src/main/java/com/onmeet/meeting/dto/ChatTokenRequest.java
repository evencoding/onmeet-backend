package com.onmeet.meeting.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatTokenRequest(
    Long roomId,
    String roomCode,
    @NotBlank String serviceIdentity
) {
}
