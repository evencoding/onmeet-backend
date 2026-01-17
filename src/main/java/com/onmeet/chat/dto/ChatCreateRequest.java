package com.onmeet.chat.dto;

import com.onmeet.chat.entity.MessageType;
import com.onmeet.chat.entity.SenderType;

public record ChatCreateRequest(
        String senderName,
        SenderType senderType,
        String messageContent
) {}
