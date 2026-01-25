package com.onmeet.chat.controller;

import com.onmeet.chat.dto.ChatCreateRequest;
import com.onmeet.chat.dto.ChatResponse;
import com.onmeet.chat.service.ChatService;
import com.onmeet.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meeting/{meetRoomId}/chats")
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ApiResponse<ChatResponse> save(
            @PathVariable UUID meetRoomId,
            @Valid @RequestBody ChatCreateRequest request
    ) {
        return ApiResponse.ok(chatService.save(meetRoomId, request));
    }

    @GetMapping
    public ApiResponse<List<ChatResponse>> getChats(
            @PathVariable UUID meetRoomId,
            @RequestParam(required = false) Instant before,
            @RequestParam(defaultValue = "50") int limit
    ) {
        // before 없으면 최초 조회, 있으면 커서 조회
        if (before == null) {
            return ApiResponse.ok(chatService.getLatest(meetRoomId, limit));
        }
        return ApiResponse.ok(chatService.getBefore(meetRoomId, before, limit));
    }
}
