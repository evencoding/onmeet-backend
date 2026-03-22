package com.onmeet.common.exception

import com.onmeet.common.exception.errorcode.AuthErrorCode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException

/**
 * BaseGlobalExceptionHandler 단위 테스트.
 * 핸들러 메서드를 직접 호출하여 ApiResponse.error 구조를 검증한다.
 */
class BaseGlobalExceptionHandlerTest {

    // Concrete subclass for testing the abstract handler
    private class TestExceptionHandler : BaseGlobalExceptionHandler()

    private lateinit var handler: TestExceptionHandler

    @BeforeEach
    fun setUp() {
        handler = TestExceptionHandler()
    }

    // ─── BusinessException ────────────────────────────────────────────────

    @Test
    fun `handleBusinessException returns errorCode status and message`() {
        val ex = BusinessException(AuthErrorCode.USER_NOT_FOUND)

        val response = handler.handleBusinessException(ex)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertNotNull(response.body)
        assertFalse(response.body!!.success)
        assertEquals("AUTH_006", response.body!!.error!!.code)
        assertEquals(404, response.body!!.error!!.status)
        assertEquals(AuthErrorCode.USER_NOT_FOUND.message, response.body!!.error!!.message)
    }

    @Test
    fun `handleBusinessException with conflict error code returns 409`() {
        val ex = BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS)

        val response = handler.handleBusinessException(ex)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("AUTH_001", response.body!!.error!!.code)
        assertEquals(409, response.body!!.error!!.status)
    }

    @Test
    fun `handleBusinessException with unauthorized error code returns 401`() {
        val ex = BusinessException(AuthErrorCode.AUTHENTICATION_FAILED)

        val response = handler.handleBusinessException(ex)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals("AUTH_004", response.body!!.error!!.code)
    }

    // ─── IllegalArgumentException ─────────────────────────────────────────

    @Test
    fun `handleIllegalArgumentException returns 400 with COMMON_BAD_REQUEST code`() {
        val ex = IllegalArgumentException("invalid input")

        val response = handler.handleIllegalArgumentException(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("COMMON_BAD_REQUEST", response.body!!.error!!.code)
        assertEquals(400, response.body!!.error!!.status)
        assertEquals("invalid input", response.body!!.error!!.message)
    }

    @Test
    fun `handleIllegalArgumentException with null message uses default`() {
        val ex = IllegalArgumentException()

        val response = handler.handleIllegalArgumentException(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("잘못된 요청입니다", response.body!!.error!!.message)
    }

    // ─── InsufficientPermissionException ─────────────────────────────────

    @Test
    @Suppress("DEPRECATION")
    fun `handleInsufficientPermissionException returns 403`() {
        val ex = InsufficientPermissionException("권한이 없습니다")

        val response = handler.handleInsufficientPermissionException(ex)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("COMMON_FORBIDDEN", response.body!!.error!!.code)
        assertEquals(403, response.body!!.error!!.status)
    }

    // ─── CrossCompanyAccessException ──────────────────────────────────────

    @Test
    @Suppress("DEPRECATION")
    fun `handleCrossCompanyAccessException returns 403 with COMMON_CROSS_COMPANY`() {
        val ex = CrossCompanyAccessException("타 회사 접근 불가")

        val response = handler.handleCrossCompanyAccessException(ex)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("COMMON_CROSS_COMPANY", response.body!!.error!!.code)
    }

    // ─── Spring Security AccessDeniedException ────────────────────────────

    @Test
    fun `handleAccessDeniedException returns 403 with COMMON_ACCESS_DENIED`() {
        val ex = AccessDeniedException("access denied")

        val response = handler.handleAccessDeniedException(ex)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("COMMON_ACCESS_DENIED", response.body!!.error!!.code)
        assertEquals("접근이 거부되었습니다", response.body!!.error!!.message)
    }

    // ─── EntityNotFoundException ──────────────────────────────────────────

    @Test
    @Suppress("DEPRECATION")
    fun `handleEntityNotFoundException returns 404 with COMMON_NOT_FOUND`() {
        val ex = EntityNotFoundException("엔티티를 찾을 수 없습니다")

        val response = handler.handleEntityNotFoundException(ex)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("COMMON_NOT_FOUND", response.body!!.error!!.code)
        assertEquals(404, response.body!!.error!!.status)
    }

    // ─── IllegalStateException ────────────────────────────────────────────

    @Test
    fun `handleIllegalStateException returns 409 with COMMON_CONFLICT`() {
        val ex = IllegalStateException("잘못된 상태")

        val response = handler.handleIllegalStateException(ex)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("COMMON_CONFLICT", response.body!!.error!!.code)
    }

    // ─── Unhandled Exception ──────────────────────────────────────────────

    @Test
    fun `handleException returns 500 with COMMON_INTERNAL_ERROR`() {
        val ex = RuntimeException("unexpected failure")

        val response = handler.handleException(ex)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("COMMON_INTERNAL_ERROR", response.body!!.error!!.code)
        assertEquals(500, response.body!!.error!!.status)
        assertEquals("서버 내부 오류가 발생했습니다", response.body!!.error!!.message)
    }

    @Test
    fun `handleException returns 500 for any unhandled exception type`() {
        val ex = NullPointerException("null somewhere")

        val response = handler.handleException(ex)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("COMMON_INTERNAL_ERROR", response.body!!.error!!.code)
    }

    // ─── ApiResponse structure ────────────────────────────────────────────

    @Test
    fun `error response has success false and no data`() {
        val ex = BusinessException(AuthErrorCode.TEAM_NOT_FOUND)

        val response = handler.handleBusinessException(ex)

        assertFalse(response.body!!.success)
        assertNull(response.body!!.data)
        assertNotNull(response.body!!.error)
    }
}
