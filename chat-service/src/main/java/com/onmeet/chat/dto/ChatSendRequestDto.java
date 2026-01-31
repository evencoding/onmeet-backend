package com.onmeet.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChatSendRequestDto(

        @NotNull
        Long meetRoomId,

        @NotBlank
        String messageType,

        String content,

        String attachmentJson
) {}
