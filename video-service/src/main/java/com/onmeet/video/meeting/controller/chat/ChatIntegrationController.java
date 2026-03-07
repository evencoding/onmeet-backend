package com.onmeet.video.meeting.controller.chat;

import com.onmeet.video.common.response.ApiResponse;
import com.onmeet.video.meeting.dto.chat.ChatTokenRequest;
import com.onmeet.video.meeting.dto.chat.ChatTokenResponse;
import com.onmeet.video.meeting.dto.chat.SendChatRequest;
import com.onmeet.video.meeting.service.chat.ChatIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat Integration", description = "회의방 채팅 연동 API")
@RestController
@RequestMapping("/api/rooms")
public class ChatIntegrationController {

    private final ChatIntegrationService chatIntegrationService;

    public ChatIntegrationController(ChatIntegrationService chatIntegrationService) {
        this.chatIntegrationService = chatIntegrationService;
    }

    @Operation(summary = "채팅 토큰 발급")
    @PostMapping("/internal/chat-token")
    public ApiResponse<ChatTokenResponse> generateChatToken(@Valid @RequestBody ChatTokenRequest request) {
        return ApiResponse.ok(chatIntegrationService.generateChatToken(request));
    }

    @Operation(summary = "채팅 메시지 전송")
    @PostMapping("/{roomId}/chat/send")
    public ApiResponse<Void> sendMessage(@PathVariable Long roomId,
                                         @Valid @RequestBody SendChatRequest request,
                                         @RequestHeader("X-User-Id") Long userId) {
        chatIntegrationService.sendMessage(roomId, request, userId);
        return ApiResponse.ok(null);
    }
}
