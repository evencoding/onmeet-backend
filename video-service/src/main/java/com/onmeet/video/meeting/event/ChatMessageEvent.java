package com.onmeet.video.meeting.event;

import java.time.Instant;

public record ChatMessageEvent(
    String messageId,
    Long roomId,
    String roomName,
    Long senderId,
    String senderIdentity,
    String messageType,
    String content,
    String replyToMessageId,
    Instant timestamp
) {
}
