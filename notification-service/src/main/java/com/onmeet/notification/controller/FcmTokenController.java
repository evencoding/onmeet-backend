package com.onmeet.notification.controller;

import com.onmeet.common.dto.ErrorResponse;
import com.onmeet.notification.dto.FcmTokenRequestDto;
import com.onmeet.notification.service.FcmService;
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
// CHECK [notification-담당자]: @RequestMapping에서 /notification prefix 제거됨.
// context-path=/notification이 이미 prefix를 추가하므로 최종 URL은
// /notification/v1/fcm 으로 동일. Swagger/API 문서 업데이트 필요.
@RequestMapping("/v1/fcm")
@Tag(name = "FCM Token", description = "FCM 토큰 관리 API")
public class FcmTokenController {

    private final FcmService fcmService;

    @Operation(
        summary = "FCM 토큰 등록",
        description = "현재 인증된 사용자의 디바이스 FCM 토큰을 등록합니다. "
            + "동일 deviceId로 이미 등록된 토큰이 있으면 토큰 값만 업데이트합니다(Upsert). "
            + "deviceType은 WEB / ANDROID / IOS 중 하나를 사용합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "FCM 토큰 등록(또는 업데이트) 성공"),
        @ApiResponse(
            responseCode = "400",
            description = "X-User-Id 헤더가 누락되었거나 요청 바디 형식이 올바르지 않은 경우",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"COMMON_BAD_REQUEST\",\"status\":400,\"message\":\"잘못된 요청입니다\",\"timestamp\":1710000000000}")
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "FCM 토큰 저장 실패",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"NOTI_014\",\"status\":500,\"message\":\"FCM 토큰 등록에 실패했습니다\",\"timestamp\":1710000000000}")
            )
        )
    })
    @PostMapping("/token")
    public ResponseEntity<Void> registerToken(
            @Parameter(description = "사용자 ID (Gateway에서 자동 주입)", required = true) @RequestHeader("X-User-Id") Long userId,
            @RequestBody FcmTokenRequestDto dto) {
        fcmService.registerToken(userId, dto);
        return ResponseEntity.ok().build();
    }

    @Operation(
        summary = "FCM 토큰 해제",
        description = "현재 인증된 사용자의 특정 FCM 토큰을 삭제합니다. "
            + "해당 토큰이 존재하지 않더라도 200을 반환합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "FCM 토큰 해제 성공"),
        @ApiResponse(
            responseCode = "400",
            description = "X-User-Id 헤더 또는 token 쿼리 파라미터가 누락된 경우",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"COMMON_BAD_REQUEST\",\"status\":400,\"message\":\"잘못된 요청입니다\",\"timestamp\":1710000000000}")
            )
        ),
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
    @DeleteMapping("/token")
    public ResponseEntity<Void> unregisterToken(
            @Parameter(description = "사용자 ID (Gateway에서 자동 주입)", required = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "해제할 FCM 토큰 값", required = true) @RequestParam String token) {
        fcmService.unregisterToken(userId, token);
        return ResponseEntity.ok().build();
    }
}
