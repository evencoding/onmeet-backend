package com.onmeet.chat.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onmeet.common.dto.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/v1")
@Tag(name = "Chat", description = "채팅 관련 서비스 API")
public class ChatController {

    @Operation(summary = "채팅 서비스 상태/정보 조회", description = "채팅 서비스의 상태 및 내 정보를 조회합니다.")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "채팅 서비스 상태 조회 성공 - 사용자 ID와 서비스 상태 반환"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "인증 실패 - 로그인이 필요합니다",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    value = "{\"status\": 401, \"message\": \"Authentication failed\", \"timestamp\": 1234567890}"
                )
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    value = "{\"status\": 500, \"message\": \"Internal server error occurred\", \"timestamp\": 1234567890}"
                )
            )
        )
    })
    @GetMapping("/me")
    public String me(@AuthenticationPrincipal String userId) {
        return "Hello from Chat Service! User ID: " + (userId != null ? userId : "Unknown");
    }

    @Operation(summary = "팀 채팅 조회 (권한 체크 예시)", description = "팀 멤버만 채팅을 조회할 수 있습니다.")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "팀 채팅 조회 성공"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "인증 실패 - 로그인이 필요합니다",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    value = "{\"status\": 401, \"message\": \"Authentication failed\", \"timestamp\": 1234567890}"
                )
            )
        ),
        @ApiResponse(
            responseCode = "403",
            description = "권한 없음 - 팀 멤버가 아님",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    value = "{\"status\": 403, \"message\": \"Access denied\", \"timestamp\": 1234567890}"
                )
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "팀을 찾을 수 없음",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    value = "{\"status\": 404, \"message\": \"Team not found\", \"timestamp\": 1234567890}"
                )
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    value = "{\"status\": 500, \"message\": \"Internal server error occurred\", \"timestamp\": 1234567890}"
                )
            )
        )
    })
    @GetMapping("/teams/{teamId}/chat")
    @PreAuthorize("@teamSecurity.isMemberOf(#teamId, principal)")
    public String getTeamChat(@PathVariable Long teamId, @AuthenticationPrincipal String userId) {
        return "Chat content for team " + teamId + " for user " + userId;
    }
}
