package com.onmeet.video.chat.controller;

import java.util.UUID;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import com.onmeet.video.chat.dto.ChatMessage;

@Controller
public class ChatController {

    @MessageMapping("/chat/{roomId}")
    @SendTo("/topic/room/{roomId}")
    public ChatMessage handleMessage(@DestinationVariable String roomId, ChatMessage message) {
        return new ChatMessage(
                UUID.randomUUID().toString(),
                message.type() != null ? message.type() : "CHAT",
                message.senderId(),
                message.senderName(),
                message.content(),
                roomId,
                System.currentTimeMillis()
        );
    }
}
