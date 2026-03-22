package com.onmeet.common.dto

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

// CHECK [common-담당자]: NotificationRequestDto에 dedupeKey, scheduledAt 추가.
// nullable + default null이므로 기존 코드 하위 호환. 다만 notification-service에서
// 이 필드를 실제 처리하는 로직이 있는지 확인 필요.
class NotificationRequestDtoTest {

    @Test
    fun `dedupeKey and scheduledAt default to null for backward compatibility`() {
        val dto = NotificationRequestDto(userId = 1L, type = "MEETING_REMINDER", title = "Test")

        assertNull(dto.dedupeKey)
        assertNull(dto.scheduledAt)
    }

    @Test
    fun `dedupeKey and scheduledAt can be set`() {
        val scheduledTime = LocalDateTime.of(2026, 3, 20, 10, 0)
        val dto = NotificationRequestDto(
            userId = 1L,
            type = "MEETING_REMINDER",
            title = "Meeting in 5 minutes",
            dedupeKey = "meeting-123-reminder",
            scheduledAt = scheduledTime
        )

        assertEquals("meeting-123-reminder", dto.dedupeKey)
        assertEquals(scheduledTime, dto.scheduledAt)
    }

    @Test
    fun `existing fields remain unchanged`() {
        val dto = NotificationRequestDto(
            userId = 10L,
            userIds = listOf(1L, 2L),
            type = "TEAM_MEMBER_ADDED",
            title = "Team",
            body = "You joined",
            deeplink = "/teams/1",
            resourceType = "TEAM",
            resourceId = "1",
            actorUserId = 5L
        )

        assertEquals(10L, dto.userId)
        assertEquals(listOf(1L, 2L), dto.userIds)
        assertEquals("TEAM_MEMBER_ADDED", dto.type)
        assertEquals("Team", dto.title)
        assertEquals("You joined", dto.body)
        assertEquals("/teams/1", dto.deeplink)
        assertEquals("TEAM", dto.resourceType)
        assertEquals("1", dto.resourceId)
        assertEquals(5L, dto.actorUserId)
    }
}
