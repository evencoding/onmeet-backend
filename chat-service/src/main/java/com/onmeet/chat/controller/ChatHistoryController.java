package com.onmeet.chat.controller;

import com.onmeet.chat.common.ApiResponse;
import com.onmeet.chat.dto.ChatHistoryResponseDto;
import com.onmeet.chat.service.ChatService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chats")
public class ChatHistoryController {

    private final ChatService chatService;

    public ChatHistoryController(ChatService chatService) {
        this.chatService = chatService;
    }

     // 채팅 히스토리 조회 (cursor 기반)
     // GET /api/chats/history?meetRoomId=10&size=50
     // GET /api/chats/history?meetRoomId=10&beforeId=151&size=50

    @GetMapping("/history")
    public ApiResponse<ChatHistoryResponseDto> history(
            @RequestParam Long meetRoomId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ApiResponse.ok(
                chatService.getHistory(meetRoomId, beforeId, size)
        );
    }
}
