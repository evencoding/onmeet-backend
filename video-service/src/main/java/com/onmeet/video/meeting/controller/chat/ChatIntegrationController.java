package com.onmeet.video.meeting.controller.chat;

import com.onmeet.common.dto.ErrorResponse;
import com.onmeet.video.common.response.ApiResponse;
import com.onmeet.video.meeting.dto.chat.ChatTokenRequest;
import com.onmeet.video.meeting.dto.chat.ChatTokenResponse;
import com.onmeet.video.meeting.dto.chat.SendChatRequest;
import com.onmeet.video.meeting.service.chat.ChatIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    @Operation(summary = "채팅 토큰 발급", description = "채팅 서비스 연동을 위한 토큰을 발급합니다. roomId 또는 roomCode 중 하나는 반드시 포함해야 합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "채팅 토큰 발급 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "roomId와 roomCode 모두 누락된 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "VIDEO_055 - roomId 또는 roomCode 필수",
                    value = "{\"code\":\"VIDEO_055\",\"status\":400,\"message\":\"roomId 또는 roomCode 중 하나는 필수입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "인증 필요",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"GATEWAY_001\",\"status\":401,\"message\":\"인증 토큰이 없습니다.\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "존재하지 않는 회의실",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "VIDEO_004 - 회의실 없음",
                    value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", description = "서버 내부 오류",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"INTERNAL_ERROR\",\"status\":500,\"message\":\"서버 내부 오류가 발생했습니다\",\"timestamp\":1710000000000}"))
        )
    })
    @PostMapping("/internal/chat-token")
    public ApiResponse<ChatTokenResponse> generateChatToken(@Valid @RequestBody ChatTokenRequest request) {
        return ApiResponse.ok(chatIntegrationService.generateChatToken(request));
    }

    @Operation(summary = "채팅 메시지 전송", description = "회의방 채팅에 메시지를 전송합니다. 해당 회의방의 참가자만 전송 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "메시지 전송 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "인증 필요",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"GATEWAY_001\",\"status\":401,\"message\":\"인증 토큰이 없습니다.\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "해당 회의방의 참가자가 아닌 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "VIDEO_056 - 참가자 아님",
                    value = "{\"code\":\"VIDEO_056\",\"status\":403,\"message\":\"해당 회의실의 참가자가 아닙니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "존재하지 않는 회의실",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "VIDEO_004 - 회의실 없음",
                    value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", description = "서버 내부 오류 - 채팅 메시지 직렬화 실패 포함",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "VIDEO_057 - 직렬화 실패",
                    value = "{\"code\":\"VIDEO_057\",\"status\":500,\"message\":\"채팅 메시지 직렬화에 실패했습니다\",\"timestamp\":1710000000000}"))
        )
    })
    @PostMapping("/{roomId}/chat/send")
    public ApiResponse<Void> sendMessage(@PathVariable Long roomId,
                                         @Valid @RequestBody SendChatRequest request,
                                         @RequestHeader("X-User-Id") Long userId) {
        chatIntegrationService.sendMessage(roomId, request, userId);
        return ApiResponse.ok(null);
    }
}
