package com.onmeet.video.meeting.dto;

import java.time.Instant;

public record DataChannelMessage(
    String messageId,
    String type,
    Long senderId,
    String senderName,
    String content,
    String replyToMessageId,
    Instant timestamp
) {

    public static final String TYPE_CHAT = "CHAT_MESSAGE";
    public static final String TYPE_REACTION = "CHAT_REACTION";
    public static final String TYPE_SYSTEM = "SYSTEM_MESSAGE";
    public static final String TYPE_FILE = "CHAT_FILE";
    public static final String TYPE_SCREEN_SHARE_START = "SCREEN_SHARE_START";
    public static final String TYPE_SCREEN_SHARE_STOP = "SCREEN_SHARE_STOP";
}
