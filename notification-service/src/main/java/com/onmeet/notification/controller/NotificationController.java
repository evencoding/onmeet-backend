package com.onmeet.notification.controller

import com.onmeet.notification.dto.NotificationResponseDto
import com.onmeet.notification.service.NotificationQueryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import lombok.RequiredArgsConstructor
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.Map

@RestController
@RequiredArgsConstructor
@RequestMapping("/notification/v1/notifications")
@Tag(name = "Notification", description = "알림 조회 및 관리 API")
class NotificationController {
    private val notificationQueryService: NotificationQueryService? = null

    @Operation(summary = "내 알림 목록 조회", description = "현재 로그인된 사용자의 알림 목록을 최신순으로 페이징하여 조회합니다.")
    @GetMapping
    fun getMyNotifications(
        @Parameter(description = "사용자 ID (Gateway에서 자동 주입)", required = true) @RequestHeader("X-User-Id") userId: Long?,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable?
    ): ResponseEntity<Page<NotificationResponseDto?>?> {
        return ResponseEntity.ok<Page<NotificationResponseDto?>?>(
            notificationQueryService!!.getMyNotifications(
                userId,
                pageable
            )
        )
    }

    @Operation(summary = "미읽음 알림 수 조회", description = "현재 로그인된 사용자의 읽지 않은 알림 개수를 반환합니다.")
    @GetMapping("/unread/count")
    fun getUnreadCount(
        @Parameter(description = "사용자 ID (Gateway에서 자동 주입)", required = true) @RequestHeader("X-User-Id") userId: Long?
    ): ResponseEntity<MutableMap<String?, Long?>?> {
        val count = notificationQueryService!!.getUnreadCount(userId)
        return ResponseEntity.ok<MutableMap<String?, Long?>?>(Map.of<String?, Long?>("unreadCount", count))
    }

    @Operation(summary = "알림 단건 읽음 처리", description = "특정 알림 ID를 읽음 처리합니다.")
    @PatchMapping("/{notificationId}/read")
    fun markAsRead(
        @Parameter(description = "알림 수신 ID", required = true) @PathVariable notificationId: Long?,
        @Parameter(description = "사용자 ID (Gateway에서 자동 주입)", required = true) @RequestHeader("X-User-Id") userId: Long?
    ): ResponseEntity<Void?> {
        notificationQueryService!!.markAsRead(notificationId, userId)
        return ResponseEntity.ok().build<Void?>()
    }

    @Operation(summary = "모든 알림 읽음 처리", description = "현재 로그인된 사용자의 모든 알림을 읽음 처리합니다.")
    @PatchMapping("/read/all")
    fun markAllAsRead(
        @Parameter(description = "사용자 ID (Gateway에서 자동 주입)", required = true) @RequestHeader("X-User-Id") userId: Long?
    ): ResponseEntity<MutableMap<String?, Int?>?> {
        val updated = notificationQueryService!!.markAllAsRead(userId)
        return ResponseEntity.ok<MutableMap<String?, Int?>?>(Map.of<String?, Int?>("updatedCount", updated))
    }

    @Operation(summary = "알림 단건 삭제", description = "특정 알림 ID를 삭제합니다.")
    @DeleteMapping("/{notificationId}")
    fun deleteNotification(
        @Parameter(description = "알림 수신 ID", required = true) @PathVariable notificationId: Long?,
        @Parameter(description = "사용자 ID (Gateway에서 자동 주입)", required = true) @RequestHeader("X-User-Id") userId: Long?
    ): ResponseEntity<Void?> {
        notificationQueryService!!.deleteNotification(notificationId, userId)
        return ResponseEntity.ok().build<Void?>()
    }

    @Operation(summary = "모든 알림 삭제", description = "현재 로그인된 사용자의 모든 알림을 삭제합니다.")
    @DeleteMapping("/all")
    fun deleteAllNotifications(
        @Parameter(description = "사용자 ID (Gateway에서 자동 주입)", required = true) @RequestHeader("X-User-Id") userId: Long?
    ): ResponseEntity<Void?> {
        notificationQueryService!!.deleteAllNotifications(userId)
        return ResponseEntity.ok().build<Void?>()
    }
}
