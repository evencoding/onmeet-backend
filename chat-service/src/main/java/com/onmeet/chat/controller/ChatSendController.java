package com.onmeet.chat.controller;

import com.onmeet.chat.common.ApiResponse;
import com.onmeet.chat.dto.ChatMessageResponseDto;
import com.onmeet.chat.dto.ChatSendRequestDto;
import com.onmeet.chat.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chats")
public class ChatSendController {

    private final ChatService chatService;

    public ChatSendController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ApiResponse<ChatMessageResponseDto> send(
            @RequestBody @Valid ChatSendRequestDto request
    ) {
        Long senderId = 1L;
        String senderName = "TEST_USER";
        String senderType = "USER";

        return ApiResponse.ok(
                chatService.sendMessage(request, senderId, senderName, senderType)
        );
    }
}
