package com.onmeet.auth.security

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

@ExtendWith(MockKExtension::class)
class GatewaySecretFilterTest {

    @MockK
    private lateinit var filterChain: FilterChain

    private val testSecret = "test-gateway-secret-value"
    private lateinit var filter: GatewaySecretFilter

    @BeforeEach
    fun setUp() {
        filter = GatewaySecretFilter(testSecret)
    }

    @Test
    fun `request with valid gateway secret should pass through filter chain`() {
        // given
        val request = MockHttpServletRequest()
        request.addHeader("X-Gateway-Secret", testSecret)
        val response = MockHttpServletResponse()
        every { filterChain.doFilter(request, response) } returns Unit

        // when
        filter.doFilter(request, response, filterChain)

        // then
        verify { filterChain.doFilter(request, response) }
        assertEquals(HttpStatus.OK.value(), response.status)
    }

    @Test
    fun `request with invalid gateway secret should return 401 Unauthorized`() {
        // given
        val request = MockHttpServletRequest()
        request.addHeader("X-Gateway-Secret", "wrong-secret")
        val response = MockHttpServletResponse()

        // when
        filter.doFilter(request, response, filterChain)

        // then
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.status)
        verify(exactly = 0) { filterChain.doFilter(any(), any()) }
    }

    @Test
    fun `request with missing gateway secret header should return 401 Unauthorized`() {
        // given
        val request = MockHttpServletRequest()
        // No X-Gateway-Secret header
        val response = MockHttpServletResponse()

        // when
        filter.doFilter(request, response, filterChain)

        // then
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.status)
        verify(exactly = 0) { filterChain.doFilter(any(), any()) }
    }

    @Test
    fun `request with empty string secret should return 401 Unauthorized`() {
        // given
        val request = MockHttpServletRequest()
        request.addHeader("X-Gateway-Secret", "")
        val response = MockHttpServletResponse()

        // when
        filter.doFilter(request, response, filterChain)

        // then
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.status)
        verify(exactly = 0) { filterChain.doFilter(any(), any()) }
    }
}
