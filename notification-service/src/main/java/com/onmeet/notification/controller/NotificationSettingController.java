package com.onmeet.notification.controller;

import com.onmeet.common.dto.ErrorResponse;
import com.onmeet.notification.dto.NotificationSettingDto;
import com.onmeet.notification.service.NotificationSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/notification/v1/settings")
@Tag(name = "Notification Settings", description = "알림 설정 API")
public class NotificationSettingController {

    private final NotificationSettingService settingService;

    @Operation(
        summary = "알림 설정 조회",
        description = "사용자의 알림 설정을 조회합니다. "
            + "설정이 없는 경우 기본값(모두 true)을 반환합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "알림 설정 조회 성공"),
        @ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"COMMON_INTERNAL_ERROR\",\"status\":500,\"message\":\"서버 내부 오류가 발생했습니다\",\"timestamp\":1710000000000}")
            )
        )
    })
    @GetMapping("/{userId}")
    public ResponseEntity<NotificationSettingDto> getSettings(
            @Parameter(description = "사용자 ID", required = true) @PathVariable Long userId) {
        return ResponseEntity.ok(settingService.getSettings(userId));
    }

    @Operation(
        summary = "알림 설정 업데이트",
        description = "사용자의 알림 설정을 업데이트합니다. "
            + "isMeetingNotification: 회의 관련 알림, isMinutesCompletedNotification: 회의록 완성 알림, "
            + "isTeamNotification: 팀 관련 알림. 설정이 없는 경우 신규 생성합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "알림 설정 업데이트 성공"),
        @ApiResponse(
            responseCode = "400",
            description = "요청 바디 형식이 올바르지 않은 경우",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"COMMON_BAD_REQUEST\",\"status\":400,\"message\":\"잘못된 요청입니다\",\"timestamp\":1710000000000}")
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "알림 설정 저장 실패",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"NOTI_012\",\"status\":500,\"message\":\"알림 설정 저장에 실패했습니다\",\"timestamp\":1710000000000}")
            )
        )
    })
    @PostMapping("/{userId}")
    public ResponseEntity<Void> updateSettings(
            @Parameter(description = "사용자 ID", required = true) @PathVariable Long userId,
            @RequestBody NotificationSettingDto dto) {
        settingService.updateSettings(userId, dto);
        return ResponseEntity.ok().build();
    }
}
