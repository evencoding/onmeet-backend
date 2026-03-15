package com.onmeet.auth.dto

import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DtoExtensionsTest {

    @Test
    fun `toUserInfoDto includes fcmDeviceToken when present`() {
        val user = User(
            id = 1L,
            email = "user@test.com",
            passwordHash = "hash",
            name = "Test User",
            company = Company(id = 1L, name = "Test Co"),
            profileImageId = 10L,
            fcmDeviceToken = "fcm-token-abc123"
        )

        val dto = user.toUserInfoDto()

        assertEquals(1L, dto.userId)
        assertEquals("Test User", dto.name)
        assertEquals("user@test.com", dto.email)
        assertEquals(10L, dto.profileImageId)
        assertEquals("fcm-token-abc123", dto.fcmDeviceToken)
    }

    @Test
    fun `toUserInfoDto fcmDeviceToken is null when not set`() {
        val user = User(
            id = 2L,
            email = "user2@test.com",
            passwordHash = "hash",
            name = "Test User2",
            company = Company(id = 1L, name = "Test Co"),
            fcmDeviceToken = null
        )

        val dto = user.toUserInfoDto()

        assertNull(dto.fcmDeviceToken)
    }
}
