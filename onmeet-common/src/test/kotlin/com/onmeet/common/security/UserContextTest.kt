package com.onmeet.common.security

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * UserContext 단위 테스트.
 * RequestContextHolder를 직접 설정하여 헤더 추출 로직을 검증한다.
 */
class UserContextTest {

    private lateinit var mockRequest: MockHttpServletRequest

    @BeforeEach
    fun setUp() {
        mockRequest = MockHttpServletRequest()
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(mockRequest))
    }

    @AfterEach
    fun tearDown() {
        RequestContextHolder.resetRequestAttributes()
    }

    // ─── getUserId ───────────────────────────────────────────────────────

    @Test
    fun `getUserId returns Optional with valid numeric userId`() {
        mockRequest.addHeader(UserContext.USER_ID_HEADER, "123")

        val result = UserContext.getUserId()

        assertTrue(result.isPresent)
        assertEquals(123L, result.get())
    }

    @Test
    fun `getUserId returns empty Optional when header is missing`() {
        val result = UserContext.getUserId()

        assertFalse(result.isPresent)
    }

    @Test
    fun `getUserId throws NumberFormatException for non-numeric value`() {
        mockRequest.addHeader(UserContext.USER_ID_HEADER, "not-a-number")

        assertThrows(NumberFormatException::class.java) {
            UserContext.getUserId()
        }
    }

    // ─── getRequiredUserId ───────────────────────────────────────────────

    @Test
    fun `getRequiredUserId returns userId when header is present`() {
        mockRequest.addHeader(UserContext.USER_ID_HEADER, "42")

        val result = UserContext.getRequiredUserId()

        assertEquals(42L, result)
    }

    @Test
    fun `getRequiredUserId throws IllegalStateException when header is missing`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            UserContext.getRequiredUserId()
        }

        assertTrue(ex.message!!.contains("User ID"))
    }

    // ─── getUserEmail ────────────────────────────────────────────────────

    @Test
    fun `getUserEmail returns Optional with email when header is present`() {
        mockRequest.addHeader(UserContext.USER_EMAIL_HEADER, "user@onmeet.io")

        val result = UserContext.getUserEmail()

        assertTrue(result.isPresent)
        assertEquals("user@onmeet.io", result.get())
    }

    @Test
    fun `getUserEmail returns empty Optional when header is missing`() {
        val result = UserContext.getUserEmail()

        assertFalse(result.isPresent)
    }

    // ─── getUserRoles ────────────────────────────────────────────────────

    @Test
    fun `getUserRoles returns Optional with roles string when header is present`() {
        mockRequest.addHeader(UserContext.USER_ROLES_HEADER, "ROLE_USER,ROLE_ADMIN")

        val result = UserContext.getUserRoles()

        assertTrue(result.isPresent)
        assertEquals("ROLE_USER,ROLE_ADMIN", result.get())
    }

    @Test
    fun `getUserRoles returns empty Optional when header is missing`() {
        val result = UserContext.getUserRoles()

        assertFalse(result.isPresent)
    }

    // ─── Non-request thread context ─────────────────────────────────────

    @Test
    fun `getUserId returns empty Optional in non-request thread context`() {
        RequestContextHolder.resetRequestAttributes()

        val result = UserContext.getUserId()

        assertFalse(result.isPresent)
    }

    @Test
    fun `getRequiredUserId throws when no request context`() {
        RequestContextHolder.resetRequestAttributes()

        assertThrows(IllegalStateException::class.java) {
            UserContext.getRequiredUserId()
        }
    }
}
