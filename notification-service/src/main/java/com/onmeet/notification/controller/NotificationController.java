package com.onmeet.notification.controller;

import com.onmeet.notification.dto.NotificationResponseDto;
import com.onmeet.notification.service.NotificationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/notification/v1/notifications")
@Tag(name = "Notification", description = "알림 조회/읽음/삭제 API")
public class NotificationController {

    private final NotificationQueryService queryService;

    @Operation(summary = "내 알림 목록 조회", description = "현재 로그인한 사용자의 알림 목록을 페이징으로 조회합니다. "
            + "기본값: page=0, size=20, 최신순 정렬")
    @GetMapping
    public ResponseEntity<Page<NotificationResponseDto>> getMyNotifications(
            @RequestHeader("X-User-Id") Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(queryService.getMyNotifications(userId, pageable));
    }

    @Operation(summary = "미읽음 알림 수 조회", description = "읽지 않은 알림의 개수를 반환합니다.")
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @RequestHeader("X-User-Id") Long userId) {
        long count = queryService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    @Operation(summary = "단건 읽음 처리", description = "특정 알림을 읽음 상태로 변경합니다.")
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        queryService.markAsRead(id, userId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "전체 읽음 처리", description = "내 모든 알림을 읽음 상태로 변경합니다.")
    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllAsRead(
            @RequestHeader("X-User-Id") Long userId) {
        int updatedCount = queryService.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of("updatedCount", updatedCount));
    }

    @Operation(summary = "단건 알림 삭제", description = "특정 알림을 삭제합니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        queryService.deleteNotification(id, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "전체 알림 삭제", description = "내 모든 알림을 삭제합니다.")
    @DeleteMapping
    public ResponseEntity<Void> deleteAllNotifications(
            @RequestHeader("X-User-Id") Long userId) {
        queryService.deleteAllNotifications(userId);
        return ResponseEntity.noContent().build();
    }
}
