package com.onmeet.notification.controller;

import com.onmeet.common.dto.ErrorResponse;
import com.onmeet.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/notification/v1/sse")
@Tag(name = "SSE", description = "실시간 알림 SSE 구독 API")
public class SseController {

    private final NotificationService notificationService;

    @Operation(
        summary = "SSE 알림 구독",
        description = "현재 인증된 사용자의 실시간 알림 스트림에 구독합니다. "
            + "로그인 후 프론트엔드에서 이 엔드포인트를 호출하여 SSE 연결을 유지해야 합니다. "
            + "멀티탭/멀티디바이스 지원 — 동일 사용자의 다중 연결이 허용됩니다. "
            + "연결 타임아웃은 60분이며, 30초마다 heartbeat 이벤트가 전송됩니다."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "SSE 구독 성공 (text/event-stream 스트림 연결 유지)"),
        @ApiResponse(
            responseCode = "400",
            description = "X-User-Id 헤더가 누락되었거나 형식이 올바르지 않은 경우",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"COMMON_BAD_REQUEST\",\"status\":400,\"message\":\"잘못된 요청입니다\",\"timestamp\":1710000000000}")
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "SSE 연결 초기화 실패 (초기 이벤트 전송 실패 또는 스트림 DB 저장 실패)",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(
                        name = "SSE 초기 이벤트 전송 실패",
                        value = "{\"code\":\"NOTI_002\",\"status\":500,\"message\":\"SSE 연결 초기 이벤트 전송에 실패했습니다\",\"timestamp\":1710000000000}"
                    ),
                    @ExampleObject(
                        name = "SSE 스트림 정보 저장 실패",
                        value = "{\"code\":\"NOTI_003\",\"status\":500,\"message\":\"SSE 스트림 정보 저장에 실패했습니다\",\"timestamp\":1710000000000}"
                    )
                }
            )
        )
    })
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
            @Parameter(description = "사용자 ID (Gateway에서 자동 주입)", required = true) @RequestHeader("X-User-Id") Long userId) {
        log.info("SSE subscribe request: userId={}", userId);
        return notificationService.subscribe(userId);
    }
}
