package com.onmeet.ai.controller;

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

@RestController
@RequestMapping("/v1")
@Tag(name = "AI", description = "AI 관련 서비스 API")
public class AiController {

    @Operation(summary = "AI 서비스 상태/정보 조회", description = "AI 서비스의 상태 및 인증된 사용자 정보를 조회합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "AI 서비스 상태 조회 성공 - 사용자 ID와 서비스 상태 반환"),
        @ApiResponse(
            responseCode = "401",
            description = "인증 실패 - 유효한 JWT 토큰이 없거나 만료된 경우",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "GATEWAY_001 - 토큰 없음",
                        value = "{\"code\":\"GATEWAY_001\",\"status\":401,\"message\":\"인증 토큰이 없습니다.\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "GATEWAY_002 - 토큰 만료",
                        value = "{\"code\":\"GATEWAY_002\",\"status\":401,\"message\":\"토큰이 만료되었습니다.\",\"timestamp\":1710000000000}")
                }
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    value = "{\"code\":\"INTERNAL_ERROR\",\"status\":500,\"message\":\"서버 내부 오류가 발생했습니다\",\"timestamp\":1710000000000}"
                )
            )
        )
    })
    @GetMapping("/me")
    public String me(@AuthenticationPrincipal String userId) {
        return "Hello from AI Service! User ID: " + (userId != null ? userId : "Unknown");
    }
}
