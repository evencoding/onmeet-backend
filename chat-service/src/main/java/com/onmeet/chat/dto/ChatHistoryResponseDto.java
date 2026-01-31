package com.onmeet.chat.dto;

import java.util.List;

public record ChatHistoryResponseDto(
        Long meetRoomId,
        List<ChatMessageResponseDto> messages,

        Long nextBeforeId,

        boolean hasMore
) {}
