package com.onmeet.video.meeting.controller.screenshare;

import com.onmeet.common.dto.ErrorResponse;
import com.onmeet.video.common.response.ApiResponse;
import com.onmeet.video.meeting.dto.screenshare.ScreenShareResponse;
import com.onmeet.video.meeting.service.screenshare.ScreenShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    @Operation(summary = "화면 공유 시작", description = "회의방에서 화면 공유를 시작합니다. 진행 중인 회의이며 화면 공유가 허용된 경우에만 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "화면 공유 시작 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "진행 중이 아닌 회의실이거나 이미 공유 중 또는 현재 공유 중이 아닌 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "VIDEO_048 - 진행 중인 회의만 화면 공유 가능",
                        value = "{\"code\":\"VIDEO_048\",\"status\":400,\"message\":\"진행 중인 회의실에서만 화면 공유를 시작할 수 있습니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "VIDEO_052 - 현재 공유 중이 아님",
                        value = "{\"code\":\"VIDEO_052\",\"status\":400,\"message\":\"현재 화면을 공유 중이지 않습니다\",\"timestamp\":1710000000000}")
                })
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "인증 필요",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"GATEWAY_001\",\"status\":401,\"message\":\"인증 토큰이 없습니다.\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "권한 없음 - 화면 공유가 허용되지 않은 회의실",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "VIDEO_049 - 화면 공유 불허",
                    value = "{\"code\":\"VIDEO_049\",\"status\":403,\"message\":\"이 회의실은 화면 공유가 허용되지 않습니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "회의실 없음 또는 참가 중이지 않은 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "VIDEO_004 - 회의실 없음",
                        value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "VIDEO_050 - 참가 중이지 않음",
                        value = "{\"code\":\"VIDEO_050\",\"status\":404,\"message\":\"회의실에 참가 중인 사용자가 아닙니다\",\"timestamp\":1710000000000}")
                })
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "이미 화면 공유 중인 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "VIDEO_051 - 이미 공유 중",
                    value = "{\"code\":\"VIDEO_051\",\"status\":409,\"message\":\"이미 화면을 공유 중입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", description = "서버 내부 오류",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"INTERNAL_ERROR\",\"status\":500,\"message\":\"서버 내부 오류가 발생했습니다\",\"timestamp\":1710000000000}"))
        )
    })
    @PostMapping("/start")
    public ApiResponse<ScreenShareResponse> start(@PathVariable Long roomId,
                                                   @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(screenShareService.startScreenShare(roomId, userId));
    }

    @Operation(summary = "화면 공유 중지", description = "자신이 진행 중인 화면 공유를 중지합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "화면 공유 중지 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "현재 화면을 공유 중이지 않은 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "VIDEO_052 - 공유 중이 아님",
                    value = "{\"code\":\"VIDEO_052\",\"status\":400,\"message\":\"현재 화면을 공유 중이지 않습니다\",\"timestamp\":1710000000000}"))
        ),
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
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", description = "서버 내부 오류",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"INTERNAL_ERROR\",\"status\":500,\"message\":\"서버 내부 오류가 발생했습니다\",\"timestamp\":1710000000000}"))
        )
    })
    @PostMapping("/stop")
    public ApiResponse<Void> stop(@PathVariable Long roomId,
                                  @RequestHeader("X-User-Id") Long userId) {
        screenShareService.stopScreenShare(roomId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "화면 공유 강제 중지", description = "다른 참가자의 화면 공유를 강제로 중지합니다. 호스트 또는 공동 호스트만 수행 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "강제 중지 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "대상 참가자가 화면을 공유 중이지 않은 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "VIDEO_053 - 대상이 공유 중이 아님",
                    value = "{\"code\":\"VIDEO_053\",\"status\":400,\"message\":\"대상 참가자가 화면을 공유 중이지 않습니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "인증 필요",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"GATEWAY_001\",\"status\":401,\"message\":\"인증 토큰이 없습니다.\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "호스트 또는 공동 호스트만 수행할 수 있는 작업",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "VIDEO_016 - 호스트/공동호스트 전용",
                    value = "{\"code\":\"VIDEO_016\",\"status\":403,\"message\":\"호스트 또는 공동 호스트만 수행할 수 있는 작업입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "존재하지 않는 roomId로 요청한 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "VIDEO_004 - 회의실 없음",
                    value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", description = "서버 내부 오류 - 화면 공유 메시지 직렬화 실패 포함",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "VIDEO_054 - 직렬화 실패",
                    value = "{\"code\":\"VIDEO_054\",\"status\":500,\"message\":\"화면 공유 메시지 직렬화에 실패했습니다\",\"timestamp\":1710000000000}"))
        )
    })
    @PostMapping("/force-stop")
    public ApiResponse<Void> forceStop(@PathVariable Long roomId,
                                       @RequestParam Long targetUserId,
                                       @RequestHeader("X-User-Id") Long userId) {
        screenShareService.forceStopScreenShare(roomId, targetUserId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "화면 공유 중인 참가자 목록 조회", description = "현재 회의방에서 화면을 공유 중인 참가자 목록을 반환합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
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
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", description = "서버 내부 오류",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"INTERNAL_ERROR\",\"status\":500,\"message\":\"서버 내부 오류가 발생했습니다\",\"timestamp\":1710000000000}"))
        )
    })
    @GetMapping("/active")
    public ApiResponse<List<ScreenShareResponse>> active(@PathVariable Long roomId) {
        return ApiResponse.ok(screenShareService.listActiveScreenShares(roomId));
    }
}
