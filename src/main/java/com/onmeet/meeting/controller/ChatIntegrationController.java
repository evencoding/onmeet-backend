package com.onmeet.meeting.controller;

import com.onmeet.common.response.ApiResponse;
import com.onmeet.meeting.dto.ChatTokenRequest;
import com.onmeet.meeting.dto.ChatTokenResponse;
import com.onmeet.meeting.dto.SendChatRequest;
import com.onmeet.meeting.service.ChatIntegrationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
public class ChatIntegrationController {

    private final ChatIntegrationService chatIntegrationService;

    public ChatIntegrationController(ChatIntegrationService chatIntegrationService) {
        this.chatIntegrationService = chatIntegrationService;
    }

    @PostMapping("/internal/chat-token")
    public ApiResponse<ChatTokenResponse> generateChatToken(@Valid @RequestBody ChatTokenRequest request) {
        return ApiResponse.ok(chatIntegrationService.generateChatToken(request));
    }

    @PostMapping("/{roomId}/chat/send")
    public ApiResponse<Void> sendMessage(@PathVariable Long roomId,
                                         @Valid @RequestBody SendChatRequest request,
                                         @RequestHeader("X-User-Id") Long userId) {
        chatIntegrationService.sendMessage(roomId, request, userId);
        return ApiResponse.ok(null);
    }
}
