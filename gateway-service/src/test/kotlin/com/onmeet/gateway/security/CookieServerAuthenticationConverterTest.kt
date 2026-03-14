package com.onmeet.gateway.security

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpCookie
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken

/**
 * CookieServerAuthenticationConverter 단위 테스트.
 * accessToken 쿠키 존재 여부에 따른 인증 토큰 추출 로직을 검증한다.
 */
class CookieServerAuthenticationConverterTest {

    private val converter = CookieServerAuthenticationConverter()

    @Test
    fun `convert extracts BearerTokenAuthenticationToken from accessToken cookie`() {
        val tokenValue = "eyJhbGciOiJSUzI1NiJ9.test.signature"
        val request = MockServerHttpRequest.get("/api/users")
            .cookie(HttpCookie("accessToken", tokenValue))
            .build()
        val exchange = MockServerWebExchange.from(request)

        val authentication = converter.convert(exchange).block()

        assertNotNull(authentication)
        assertInstanceOf(BearerTokenAuthenticationToken::class.java, authentication)
        assertEquals(tokenValue, (authentication as BearerTokenAuthenticationToken).token)
    }

    @Test
    fun `convert returns empty Mono when accessToken cookie is absent`() {
        val request = MockServerHttpRequest.get("/api/users").build()
        val exchange = MockServerWebExchange.from(request)

        val authentication = converter.convert(exchange).blockOptional()

        assertTrue(authentication.isEmpty)
    }

    @Test
    fun `convert ignores unrelated cookies`() {
        val request = MockServerHttpRequest.get("/api/users")
            .cookie(HttpCookie("sessionId", "some-session"))
            .cookie(HttpCookie("trackingId", "tracker-123"))
            .build()
        val exchange = MockServerWebExchange.from(request)

        val authentication = converter.convert(exchange).blockOptional()

        assertTrue(authentication.isEmpty, "Should return empty when only unrelated cookies are present")
    }

    @Test
    fun `convert uses accessToken cookie value verbatim as bearer token`() {
        val rawToken = "header.payload.signature"
        val request = MockServerHttpRequest.get("/api/profile")
            .cookie(HttpCookie("accessToken", rawToken))
            .build()
        val exchange = MockServerWebExchange.from(request)

        val authentication = converter.convert(exchange).block() as BearerTokenAuthenticationToken

        assertEquals(rawToken, authentication.token)
    }

    @Test
    fun `convert handles multiple cookies and picks accessToken`() {
        val tokenValue = "valid.jwt.token"
        val request = MockServerHttpRequest.get("/api/data")
            .cookie(HttpCookie("refreshToken", "refresh.token.value"))
            .cookie(HttpCookie("accessToken", tokenValue))
            .cookie(HttpCookie("locale", "ko-KR"))
            .build()
        val exchange = MockServerWebExchange.from(request)

        val authentication = converter.convert(exchange).block() as BearerTokenAuthenticationToken

        assertEquals(tokenValue, authentication.token)
    }
}
