package com.onmeet.video.meeting.controller.recording;

import com.onmeet.common.dto.ErrorResponse;
import com.onmeet.video.common.response.ApiResponse;
import com.onmeet.video.meeting.dto.recording.RoomRecordingResponse;
import com.onmeet.video.meeting.service.recording.RoomRecordingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    @Operation(summary = "녹화 시작", description = "진행 중인 회의의 녹화를 시작합니다. 녹화가 비활성화된 방이거나 이미 녹화 중인 경우 불가합니다. 호스트만 수행 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "녹화 시작 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "진행 중이지 않은 회의실에서 녹화 시작 시도",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "VIDEO_041 - 진행 중인 회의만 녹화 가능",
                    value = "{\"code\": \"VIDEO_041\", \"status\": 400, \"message\": \"진행 중인 회의실에서만 녹음을 시작할 수 있습니다\", \"timestamp\": 1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "인증 필요",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\": \"GATEWAY_001\", \"status\": 401, \"message\": \"인증 토큰이 없습니다.\", \"timestamp\": 1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "권한 없음 - 호스트 전용 또는 녹화 비활성화",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "VIDEO_015 - 호스트 전용",
                        value = "{\"code\": \"VIDEO_015\", \"status\": 403, \"message\": \"호스트만 수행할 수 있는 작업입니다\", \"timestamp\": 1710000000000}"),
                    @ExampleObject(name = "VIDEO_042 - 녹화 비활성화",
                        value = "{\"code\": \"VIDEO_042\", \"status\": 403, \"message\": \"이 회의실은 녹음이 비활성화되어 있습니다\", \"timestamp\": 1710000000000}")
                })
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "존재하지 않는 roomId로 요청한 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "VIDEO_004 - 회의실 없음",
                    value = "{\"code\": \"VIDEO_004\", \"status\": 404, \"message\": \"존재하지 않는 회의실입니다\", \"timestamp\": 1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "이미 녹화가 진행 중인 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "VIDEO_043 - 녹화 중복",
                    value = "{\"code\": \"VIDEO_043\", \"status\": 409, \"message\": \"녹음이 이미 진행 중입니다\", \"timestamp\": 1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", description = "서버 내부 오류",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\": \"INTERNAL_ERROR\", \"status\": 500, \"message\": \"서버 내부 오류가 발생했습니다\", \"timestamp\": 1710000000000}"))
        )
    })
    @PostMapping("/api/rooms/{roomId}/recording/start")
    public ApiResponse<List<RoomRecordingResponse>> startRecording(@PathVariable Long roomId,
                                                                   @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(recordingService.startRecording(roomId, userId));
    }

    @Operation(summary = "녹화 중지", description = "진행 중인 녹화를 중지합니다. 호스트만 수행 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "녹화 중지 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "인증 필요",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\": \"GATEWAY_001\", \"status\": 401, \"message\": \"인증 토큰이 없습니다.\", \"timestamp\": 1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "호스트만 수행할 수 있는 작업",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "VIDEO_015 - 호스트 전용",
                    value = "{\"code\": \"VIDEO_015\", \"status\": 403, \"message\": \"호스트만 수행할 수 있는 작업입니다\", \"timestamp\": 1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "회의실 없음 또는 진행 중인 녹화 없음",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "VIDEO_004 - 회의실 없음",
                        value = "{\"code\": \"VIDEO_004\", \"status\": 404, \"message\": \"존재하지 않는 회의실입니다\", \"timestamp\": 1710000000000}"),
                    @ExampleObject(name = "VIDEO_044 - 진행 중인 녹화 없음",
                        value = "{\"code\": \"VIDEO_044\", \"status\": 404, \"message\": \"진행 중인 녹음을 찾을 수 없습니다\", \"timestamp\": 1710000000000}")
                })
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", description = "서버 내부 오류",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\": \"INTERNAL_ERROR\", \"status\": 500, \"message\": \"서버 내부 오류가 발생했습니다\", \"timestamp\": 1710000000000}"))
        )
    })
    @PostMapping("/api/rooms/{roomId}/recording/stop")
    public ApiResponse<Void> stopRecording(@PathVariable Long roomId,
                                           @RequestHeader("X-User-Id") Long userId) {
        recordingService.stopRecording(roomId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "녹화 상태 조회", description = "현재 진행 중인 녹화의 상태를 조회합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상태 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "인증 필요",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\": \"GATEWAY_001\", \"status\": 401, \"message\": \"인증 토큰이 없습니다.\", \"timestamp\": 1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "존재하지 않는 roomId로 요청한 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "VIDEO_004 - 회의실 없음",
                    value = "{\"code\": \"VIDEO_004\", \"status\": 404, \"message\": \"존재하지 않는 회의실입니다\", \"timestamp\": 1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", description = "서버 내부 오류",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\": \"INTERNAL_ERROR\", \"status\": 500, \"message\": \"서버 내부 오류가 발생했습니다\", \"timestamp\": 1710000000000}"))
        )
    })
    @GetMapping("/api/rooms/{roomId}/recording/status")
    public ApiResponse<List<RoomRecordingResponse>> getRecordingStatus(@PathVariable Long roomId) {
        return ApiResponse.ok(recordingService.getRecordingStatus(roomId));
    }

    @Operation(summary = "녹화 목록 조회", description = "회의방의 완료된 녹화 파일 목록을 조회합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "목록 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "인증 필요",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\": \"GATEWAY_001\", \"status\": 401, \"message\": \"인증 토큰이 없습니다.\", \"timestamp\": 1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "존재하지 않는 roomId로 요청한 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "VIDEO_004 - 회의실 없음",
                    value = "{\"code\": \"VIDEO_004\", \"status\": 404, \"message\": \"존재하지 않는 회의실입니다\", \"timestamp\": 1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", description = "서버 내부 오류",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\": \"INTERNAL_ERROR\", \"status\": 500, \"message\": \"서버 내부 오류가 발생했습니다\", \"timestamp\": 1710000000000}"))
        )
    })
    @GetMapping("/api/rooms/{roomId}/recordings")
    public ApiResponse<List<RoomRecordingResponse>> listRecordings(@PathVariable Long roomId) {
        return ApiResponse.ok(recordingService.listRecordings(roomId));
    }

    @Operation(summary = "녹화 파일 다운로드 URL 조회", description = "녹화 파일의 임시 다운로드 URL을 반환합니다. 녹화가 완료되어야 하며 S3에 업로드된 경우에만 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "다운로드 URL 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "녹화가 아직 완료되지 않은 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "VIDEO_046 - 녹화 미완료",
                    value = "{\"code\": \"VIDEO_046\", \"status\": 400, \"message\": \"녹음이 아직 완료되지 않았습니다\", \"timestamp\": 1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "인증 필요",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\": \"GATEWAY_001\", \"status\": 401, \"message\": \"인증 토큰이 없습니다.\", \"timestamp\": 1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "녹화 파일 없음 또는 S3 업로드 미완료",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "VIDEO_045 - 녹화 파일 없음",
                        value = "{\"code\": \"VIDEO_045\", \"status\": 404, \"message\": \"녹음 파일을 찾을 수 없습니다\", \"timestamp\": 1710000000000}"),
                    @ExampleObject(name = "VIDEO_047 - S3 업로드 미완료",
                        value = "{\"code\": \"VIDEO_047\", \"status\": 404, \"message\": \"녹음 파일이 아직 업로드되지 않았습니다\", \"timestamp\": 1710000000000}")
                })
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", description = "서버 내부 오류",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\": \"INTERNAL_ERROR\", \"status\": 500, \"message\": \"서버 내부 오류가 발생했습니다\", \"timestamp\": 1710000000000}"))
        )
    })
    @GetMapping("/api/recordings/{recordingId}/download")
    public ApiResponse<String> getDownloadUrl(@PathVariable Long recordingId) {
        return ApiResponse.ok(recordingService.getDownloadUrl(recordingId));
    }

    @Operation(summary = "녹화 파일 삭제", description = "녹화 파일을 삭제합니다. 호스트만 삭제 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "인증 필요",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\": \"GATEWAY_001\", \"status\": 401, \"message\": \"인증 토큰이 없습니다.\", \"timestamp\": 1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "호스트만 수행할 수 있는 작업",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "VIDEO_015 - 호스트 전용",
                    value = "{\"code\": \"VIDEO_015\", \"status\": 403, \"message\": \"호스트만 수행할 수 있는 작업입니다\", \"timestamp\": 1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "존재하지 않는 recordingId로 요청한 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "VIDEO_045 - 녹화 파일 없음",
                    value = "{\"code\": \"VIDEO_045\", \"status\": 404, \"message\": \"녹음 파일을 찾을 수 없습니다\", \"timestamp\": 1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", description = "서버 내부 오류",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\": \"INTERNAL_ERROR\", \"status\": 500, \"message\": \"서버 내부 오류가 발생했습니다\", \"timestamp\": 1710000000000}"))
        )
    })
    @DeleteMapping("/api/recordings/{recordingId}")
    public ApiResponse<Void> deleteRecording(@PathVariable Long recordingId,
                                             @RequestHeader("X-User-Id") Long userId) {
        recordingService.deleteRecording(recordingId, userId);
        return ApiResponse.ok(null);
    }
}
