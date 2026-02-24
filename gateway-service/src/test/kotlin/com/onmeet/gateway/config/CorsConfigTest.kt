package com.onmeet.gateway.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.cors.CorsConfiguration

class CorsConfigTest {

    @Test
    // [Essential] CORS 설정 검증 - localhost 및 127.0.0.1 허용
    fun `should configure CORS to allow localhost and 127_0_0_1`() {
        // given
        val securityConfig = SecurityConfig(
            cookieServerAuthenticationConverter = org.mockito.Mockito.mock(
                com.onmeet.gateway.security.CookieServerAuthenticationConverter::class.java
            ),
            jwkSetUri = "http://auth-service:8081/auth/v1/.well-known/jwks.json"
        )

        // when
        val corsSource = securityConfig.corsConfigurationSource()
        val request = MockServerHttpRequest.get("/").build()
        val exchange = MockServerWebExchange.from(request)
        val corsConfig = corsSource.getCorsConfiguration(exchange)

        // then
        assertNotNull(corsConfig)
        assertEquals(listOf("http://localhost:*", "http://127.0.0.1:*"), corsConfig?.allowedOriginPatterns)
        assertTrue(corsConfig?.allowedMethods?.contains("GET") == true)
        assertTrue(corsConfig?.allowedMethods?.contains("POST") == true)
        assertTrue(corsConfig?.allowedMethods?.contains("PUT") == true)
        assertTrue(corsConfig?.allowedMethods?.contains("DELETE") == true)
        assertTrue(corsConfig?.allowedHeaders?.contains("*") == true)
        assertEquals(true, corsConfig?.allowCredentials)
    }

    @Test
    // [Necessary Infrastructure] CORS 설정에 모든 필수 HTTP 메서드 포함 확인
    fun `CORS configuration should include all necessary HTTP methods`() {
        // given
        val securityConfig = SecurityConfig(
            cookieServerAuthenticationConverter = org.mockito.Mockito.mock(
                com.onmeet.gateway.security.CookieServerAuthenticationConverter::class.java
            ),
            jwkSetUri = "http://auth-service:8081/auth/v1/.well-known/jwks.json"
        )

        // when
        val corsSource = securityConfig.corsConfigurationSource()
        val request = MockServerHttpRequest.get("/").build()
        val exchange = MockServerWebExchange.from(request)
        val corsConfig = corsSource.getCorsConfiguration(exchange)

        // then
        val requiredMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
        requiredMethods.forEach { method ->
            assertTrue(
                corsConfig?.allowedMethods?.contains(method) == true,
                "CORS should allow $method method"
            )
        }
    }

    @Test
    // [Essential] Credentials 허용 설정 검증 - 쿠키 기반 인증에 필수
    fun `CORS should allow credentials for cookie-based authentication`() {
        // given
        val securityConfig = SecurityConfig(
            cookieServerAuthenticationConverter = org.mockito.Mockito.mock(
                com.onmeet.gateway.security.CookieServerAuthenticationConverter::class.java
            ),
            jwkSetUri = "http://auth-service:8081/auth/v1/.well-known/jwks.json"
        )

        // when
        val corsSource = securityConfig.corsConfigurationSource()
        val request = MockServerHttpRequest.get("/").build()
        val exchange = MockServerWebExchange.from(request)
        val corsConfig = corsSource.getCorsConfiguration(exchange)

        // then
        assertEquals(true, corsConfig?.allowCredentials,
            "CORS must allow credentials for cookie-based JWT authentication")
    }
}
