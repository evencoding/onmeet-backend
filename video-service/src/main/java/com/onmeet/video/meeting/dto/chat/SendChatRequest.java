package com.onmeet.video.meeting.dto.chat;

import jakarta.validation.constraints.NotBlank;

public record SendChatRequest(
    @NotBlank String content,
    String type,
    String replyToMessageId,
    Long destinationUserId
) {
}
