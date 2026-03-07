package com.onmeet.video.chat.dto;

public record ChatMessage(
        String messageId,
        String type,
        String senderId,
        String senderName,
        String content,
        String roomId,
        long timestamp
) {
}
