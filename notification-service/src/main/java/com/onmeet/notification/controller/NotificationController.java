package com.onmeet.notification.controller;

import com.onmeet.common.dto.ErrorResponse;
import com.onmeet.notification.dto.NotificationResponseDto;
import com.onmeet.notification.service.NotificationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/notification/v1/notifications")
@Tag(name = "Notification", description = "알림 조회 및 관리 API")
public class NotificationController {

    private final NotificationQueryService notificationQueryService;

    @Operation(
        summary = "내 알림 목록 조회",
        description = "현재 로그인된 사용자의 알림 목록을 최신순으로 페이징하여 조회합니다. "
            + "size 기본값은 20이며, createdAt 내림차순으로 정렬됩니다."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "알림 목록 조회 성공"),
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
            description = "서버 내부 오류",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"COMMON_INTERNAL_ERROR\",\"status\":500,\"message\":\"서버 내부 오류가 발생했습니다\",\"timestamp\":1710000000000}")
            )
        )
    })
    @GetMapping
    public ResponseEntity<Page<NotificationResponseDto>> getMyNotifications(
            @Parameter(description = "사용자 ID (Gateway에서 자동 주입)", required = true) @RequestHeader("X-User-Id") Long userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(notificationQueryService.getMyNotifications(userId, pageable));
    }

    @Operation(
        summary = "미읽음 알림 수 조회",
        description = "현재 로그인된 사용자의 읽지 않은 알림 개수를 반환합니다. 응답 형식: {\"unreadCount\": N}"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "미읽음 알림 수 조회 성공"),
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
            description = "서버 내부 오류",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"COMMON_INTERNAL_ERROR\",\"status\":500,\"message\":\"서버 내부 오류가 발생했습니다\",\"timestamp\":1710000000000}")
            )
        )
    })
    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @Parameter(description = "사용자 ID (Gateway에서 자동 주입)", required = true) @RequestHeader("X-User-Id") Long userId) {
        long count = notificationQueryService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    @Operation(
        summary = "알림 단건 읽음 처리",
        description = "notificationId에 해당하는 알림을 읽음 처리합니다. "
            + "현재 사용자 소유의 알림이 아니거나 존재하지 않는 ID인 경우 404를 반환합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "알림 읽음 처리 성공"),
        @ApiResponse(
            responseCode = "400",
            description = "X-User-Id 헤더가 누락되었거나 notificationId가 숫자가 아닌 경우",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"COMMON_BAD_REQUEST\",\"status\":400,\"message\":\"잘못된 요청입니다\",\"timestamp\":1710000000000}")
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 알림 ID이거나, 현재 사용자의 알림이 아닌 경우",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"NOTI_010\",\"status\":404,\"message\":\"해당 알림을 찾을 수 없습니다\",\"timestamp\":1710000000000}")
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
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
            @Parameter(description = "알림 수신 ID", required = true) @PathVariable Long notificationId,
            @Parameter(description = "사용자 ID (Gateway에서 자동 주입)", required = true) @RequestHeader("X-User-Id") Long userId) {
        notificationQueryService.markAsRead(notificationId, userId);
        return ResponseEntity.ok().build();
    }

    @Operation(
        summary = "모든 알림 읽음 처리",
        description = "현재 로그인된 사용자의 읽지 않은 모든 알림을 일괄 읽음 처리합니다. "
            + "응답 형식: {\"updatedCount\": N} (처리된 알림 수 반환)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "모든 알림 읽음 처리 성공"),
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
            description = "서버 내부 오류",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"COMMON_INTERNAL_ERROR\",\"status\":500,\"message\":\"서버 내부 오류가 발생했습니다\",\"timestamp\":1710000000000}")
            )
        )
    })
    @PatchMapping("/read/all")
    public ResponseEntity<Map<String, Integer>> markAllAsRead(
            @Parameter(description = "사용자 ID (Gateway에서 자동 주입)", required = true) @RequestHeader("X-User-Id") Long userId) {
        int updated = notificationQueryService.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of("updatedCount", updated));
    }

    @Operation(
        summary = "알림 단건 삭제",
        description = "notificationId에 해당하는 알림을 삭제합니다. "
            + "현재 사용자 소유의 알림이 아니거나 존재하지 않는 ID인 경우 404를 반환합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "알림 삭제 성공"),
        @ApiResponse(
            responseCode = "400",
            description = "X-User-Id 헤더가 누락되었거나 notificationId가 숫자가 아닌 경우",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"COMMON_BAD_REQUEST\",\"status\":400,\"message\":\"잘못된 요청입니다\",\"timestamp\":1710000000000}")
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 알림 ID이거나, 현재 사용자의 알림이 아닌 경우",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"NOTI_010\",\"status\":404,\"message\":\"해당 알림을 찾을 수 없습니다\",\"timestamp\":1710000000000}")
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
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(
            @Parameter(description = "알림 수신 ID", required = true) @PathVariable Long notificationId,
            @Parameter(description = "사용자 ID (Gateway에서 자동 주입)", required = true) @RequestHeader("X-User-Id") Long userId) {
        notificationQueryService.deleteNotification(notificationId, userId);
        return ResponseEntity.ok().build();
    }

    @Operation(
        summary = "모든 알림 삭제",
        description = "현재 로그인된 사용자의 모든 알림을 일괄 삭제합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "모든 알림 삭제 성공"),
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
            description = "서버 내부 오류",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"COMMON_INTERNAL_ERROR\",\"status\":500,\"message\":\"서버 내부 오류가 발생했습니다\",\"timestamp\":1710000000000}")
            )
        )
    })
    @DeleteMapping("/all")
    public ResponseEntity<Void> deleteAllNotifications(
            @Parameter(description = "사용자 ID (Gateway에서 자동 주입)", required = true) @RequestHeader("X-User-Id") Long userId) {
        notificationQueryService.deleteAllNotifications(userId);
        return ResponseEntity.ok().build();
    }
}
