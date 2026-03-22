package com.onmeet.video.meeting.controller.waiting;

import com.onmeet.common.dto.ErrorResponse;
import com.onmeet.video.meeting.service.waiting.WaitingRoomSseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Waiting Room SSE", description = "대기실 실시간 알림 SSE API")
@RestController
// CHECK [video-담당자]: URL 패턴 /api/rooms → /v1/rooms 변경 (gateway /video/v1/** 라우팅 통일)
@RequestMapping("/v1/rooms/{roomId}/waiting/sse")
public class WaitingRoomSseController {

    private final WaitingRoomSseService waitingRoomSseService;

    public WaitingRoomSseController(WaitingRoomSseService waitingRoomSseService) {
        this.waitingRoomSseService = waitingRoomSseService;
    }

    @Operation(summary = "대기 참여자 SSE 구독", description = "대기실에서 참여자가 입장 허가/거절 알림을 실시간으로 수신합니다. 연결 유지 중 서버에서 이벤트를 스트리밍합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "SSE 스트림 연결 성공 - text/event-stream 형태로 이벤트 수신"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "인증 필요",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"GATEWAY_001\",\"status\":401,\"message\":\"인증 토큰이 없습니다.\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "존재하지 않는 roomId로 요청한 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "VIDEO_004 - 회의실 없음",
                    value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"))
        )
    })
    @GetMapping(value = "/participant", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeParticipant(@PathVariable Long roomId,
                                           @RequestHeader("X-User-Id") Long userId) {
        return waitingRoomSseService.subscribeParticipant(roomId, userId);
    }

    @Operation(summary = "호스트 대기실 SSE 구독", description = "호스트가 대기실에 새로운 참여자 입장 요청을 실시간으로 수신합니다. 호스트만 사용 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "SSE 스트림 연결 성공 - text/event-stream 형태로 이벤트 수신"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "인증 필요",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"GATEWAY_001\",\"status\":401,\"message\":\"인증 토큰이 없습니다.\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "호스트만 구독 가능",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "VIDEO_015 - 호스트 전용",
                    value = "{\"code\":\"VIDEO_015\",\"status\":403,\"message\":\"호스트만 수행할 수 있는 작업입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "존재하지 않는 roomId로 요청한 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "VIDEO_004 - 회의실 없음",
                    value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"))
        )
    })
    @GetMapping(value = "/host", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeHost(@PathVariable Long roomId,
                                    @RequestHeader("X-User-Id") Long userId) {
        return waitingRoomSseService.subscribeHost(roomId, userId);
    }
}
