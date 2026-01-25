package com.onmeet.chat.dto;

import com.onmeet.chat.entity.Chat;
import com.onmeet.chat.entity.MessageType;
import com.onmeet.chat.entity.SenderType;

import java.time.Instant;
import java.util.UUID;

public record ChatResponse(
        Long id,
        UUID meetRoomId,
        String senderName,
        SenderType senderType,
        String messageContent,
        MessageType messageType,
        Instant createdAt
) {
    public static ChatResponse from(Chat chat) {
        return new ChatResponse(
                chat.getId(),
                chat.getMeetRoomId(),
                chat.getSenderName(),
                chat.getSenderType(),
                chat.getMessageContent(),
                chat.getMessageType(),
                chat.getCreatedAt()
        );
    }
}
