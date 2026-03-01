package com.onmeet.video.meeting.controller;

import com.onmeet.video.common.response.ApiResponse;
import com.onmeet.video.meeting.dto.RoomRecordingResponse;
import com.onmeet.video.meeting.service.RoomRecordingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Room Recording", description = "회의방 녹화 관리 API")
@RestController
public class RoomRecordingController {

    private final RoomRecordingService recordingService;

    public RoomRecordingController(RoomRecordingService recordingService) {
        this.recordingService = recordingService;
    }

    @Operation(summary = "녹화 시작")
    @PostMapping("/api/rooms/{roomId}/recording/start")
    public ApiResponse<List<RoomRecordingResponse>> startRecording(@PathVariable Long roomId,
                                                                   @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(recordingService.startRecording(roomId, userId));
    }

    @Operation(summary = "녹화 중지")
    @PostMapping("/api/rooms/{roomId}/recording/stop")
    public ApiResponse<Void> stopRecording(@PathVariable Long roomId,
                                           @RequestHeader("X-User-Id") Long userId) {
        recordingService.stopRecording(roomId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "녹화 상태 조회")
    @GetMapping("/api/rooms/{roomId}/recording/status")
    public ApiResponse<List<RoomRecordingResponse>> getRecordingStatus(@PathVariable Long roomId) {
        return ApiResponse.ok(recordingService.getRecordingStatus(roomId));
    }

    @Operation(summary = "녹화 목록 조회")
    @GetMapping("/api/rooms/{roomId}/recordings")
    public ApiResponse<List<RoomRecordingResponse>> listRecordings(@PathVariable Long roomId) {
        return ApiResponse.ok(recordingService.listRecordings(roomId));
    }

    @Operation(summary = "녹화 파일 다운로드 URL 조회")
    @GetMapping("/api/recordings/{recordingId}/download")
    public ApiResponse<String> getDownloadUrl(@PathVariable Long recordingId) {
        return ApiResponse.ok(recordingService.getDownloadUrl(recordingId));
    }

    @Operation(summary = "녹화 파일 삭제")
    @DeleteMapping("/api/recordings/{recordingId}")
    public ApiResponse<Void> deleteRecording(@PathVariable Long recordingId,
                                             @RequestHeader("X-User-Id") Long userId) {
        recordingService.deleteRecording(recordingId, userId);
        return ApiResponse.ok(null);
    }
}
