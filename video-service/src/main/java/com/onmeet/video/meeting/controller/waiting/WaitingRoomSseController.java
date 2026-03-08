package com.onmeet.video.meeting.controller.waiting;

import com.onmeet.video.meeting.service.waiting.WaitingRoomSseService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/rooms/{roomId}/waiting/sse")
public class WaitingRoomSseController {

    private final WaitingRoomSseService waitingRoomSseService;

    public WaitingRoomSseController(WaitingRoomSseService waitingRoomSseService) {
        this.waitingRoomSseService = waitingRoomSseService;
    }

    @Operation(summary = "대기 참여자 SSE 구독")
    @GetMapping(value = "/participant", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeParticipant(@PathVariable Long roomId,
                                           @RequestHeader("X-User-Id") Long userId) {
        return waitingRoomSseService.subscribeParticipant(roomId, userId);
    }

    @Operation(summary = "호스트 대기실 SSE 구독")
    @GetMapping(value = "/host", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeHost(@PathVariable Long roomId,
                                    @RequestHeader("X-User-Id") Long userId) {
        return waitingRoomSseService.subscribeHost(roomId, userId);
    }
}
