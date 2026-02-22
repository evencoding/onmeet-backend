package com.onmeet.meeting.dto;

import jakarta.validation.constraints.NotBlank;

public record SendChatRequest(
    @NotBlank String content,
    String type,
    String replyToMessageId,
    Long destinationUserId
) {
}
