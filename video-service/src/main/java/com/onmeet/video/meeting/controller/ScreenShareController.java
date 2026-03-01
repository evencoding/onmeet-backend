package com.onmeet.video.meeting.controller;

import com.onmeet.video.common.response.ApiResponse;
import com.onmeet.video.meeting.dto.ScreenShareResponse;
import com.onmeet.video.meeting.service.ScreenShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Screen Share", description = "회의방 화면 공유 API")
@RestController
@RequestMapping("/api/rooms/{roomId}/screen-share")
public class ScreenShareController {

    private final ScreenShareService screenShareService;

    public ScreenShareController(ScreenShareService screenShareService) {
        this.screenShareService = screenShareService;
    }

    @Operation(summary = "화면 공유 시작")
    @PostMapping("/start")
    public ApiResponse<ScreenShareResponse> start(@PathVariable Long roomId,
                                                   @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(screenShareService.startScreenShare(roomId, userId));
    }

    @Operation(summary = "화면 공유 중지")
    @PostMapping("/stop")
    public ApiResponse<Void> stop(@PathVariable Long roomId,
                                  @RequestHeader("X-User-Id") Long userId) {
        screenShareService.stopScreenShare(roomId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "화면 공유 강제 중지")
    @PostMapping("/force-stop")
    public ApiResponse<Void> forceStop(@PathVariable Long roomId,
                                       @RequestParam Long targetUserId,
                                       @RequestHeader("X-User-Id") Long userId) {
        screenShareService.forceStopScreenShare(roomId, targetUserId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "화면 공유 중인 참가자 목록 조회")
    @GetMapping("/active")
    public ApiResponse<List<ScreenShareResponse>> active(@PathVariable Long roomId) {
        return ApiResponse.ok(screenShareService.listActiveScreenShares(roomId));
    }
}
