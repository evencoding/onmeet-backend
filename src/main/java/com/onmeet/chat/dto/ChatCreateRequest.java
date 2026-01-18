package com.onmeet.chat.dto;

import com.onmeet.chat.entity.SenderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChatCreateRequest(
        @NotBlank String senderName,
        @NotNull SenderType senderType,
        @NotBlank String messageContent
) {}
