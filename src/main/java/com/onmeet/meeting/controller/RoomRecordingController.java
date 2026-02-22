package com.onmeet.meeting.controller;

import com.onmeet.common.response.ApiResponse;
import com.onmeet.meeting.dto.RoomRecordingResponse;
import com.onmeet.meeting.service.RoomRecordingService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RoomRecordingController {

    private final RoomRecordingService recordingService;

    public RoomRecordingController(RoomRecordingService recordingService) {
        this.recordingService = recordingService;
    }

    @PostMapping("/api/rooms/{roomId}/recording/start")
    public ApiResponse<List<RoomRecordingResponse>> startRecording(@PathVariable Long roomId,
                                                                   @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(recordingService.startRecording(roomId, userId));
    }

    @PostMapping("/api/rooms/{roomId}/recording/stop")
    public ApiResponse<Void> stopRecording(@PathVariable Long roomId,
                                           @RequestHeader("X-User-Id") Long userId) {
        recordingService.stopRecording(roomId, userId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/rooms/{roomId}/recording/status")
    public ApiResponse<List<RoomRecordingResponse>> getRecordingStatus(@PathVariable Long roomId) {
        return ApiResponse.ok(recordingService.getRecordingStatus(roomId));
    }

    @GetMapping("/api/rooms/{roomId}/recordings")
    public ApiResponse<List<RoomRecordingResponse>> listRecordings(@PathVariable Long roomId) {
        return ApiResponse.ok(recordingService.listRecordings(roomId));
    }

    @GetMapping("/api/recordings/{recordingId}/download")
    public ApiResponse<String> getDownloadUrl(@PathVariable Long recordingId) {
        return ApiResponse.ok(recordingService.getDownloadUrl(recordingId));
    }

    @DeleteMapping("/api/recordings/{recordingId}")
    public ApiResponse<Void> deleteRecording(@PathVariable Long recordingId,
                                             @RequestHeader("X-User-Id") Long userId) {
        recordingService.deleteRecording(recordingId, userId);
        return ApiResponse.ok(null);
    }
}
