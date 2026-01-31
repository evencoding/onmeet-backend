package com.onmeet.chat.dto;

import java.time.LocalDateTime;

public record ChatMessageResponseDto(
        Long id,
        Long meetRoomId,

        Long senderId,
        String senderName,
        String senderType,
        String messageType,
        String content,

        String attachmentJson,

        LocalDateTime createdAt
) {}
