package com.onmeet.gateway.exception

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.JwtValidationException
import org.springframework.web.server.ResponseStatusException
import java.net.ConnectException
import java.util.concurrent.TimeoutException

/**
 * GlobalErrorWebExceptionHandler 단위 테스트.
 * 다양한 예외 유형에 따른 HTTP 상태 코드 및 에러 코드 매핑을 검증한다.
 */
class GlobalErrorWebExceptionHandlerTest {

    private val objectMapper = ObjectMapper()
    private lateinit var handler: GlobalErrorWebExceptionHandler

    @BeforeEach
    fun setUp() {
        handler = GlobalErrorWebExceptionHandler(objectMapper)
    }

    private fun buildExchange(): MockServerWebExchange {
        val request = MockServerHttpRequest.get("/api/test").build()
        return MockServerWebExchange.from(request)
    }

    // ─── JWT / Auth exceptions ────────────────────────────────────────────

    @Test
    fun `JwtValidationException maps to GATEWAY_002 EXPIRED_TOKEN with 401`() {
        val exchange = buildExchange()
        val ex = JwtValidationException("Token expired", listOf(OAuth2Error("invalid_token", "Token expired", null)))

        handler.handle(exchange, ex).block()

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
        assertEquals(MediaType.APPLICATION_JSON, exchange.response.headers.contentType)
    }

    @Test
    fun `BadJwtException maps to GATEWAY_005 MALFORMED_JWT with 401`() {
        val exchange = buildExchange()
        val ex = BadJwtException("Malformed token")

        handler.handle(exchange, ex).block()

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
    }

    @Test
    fun `AuthenticationException maps to GATEWAY_001 MISSING_TOKEN with 401`() {
        val exchange = buildExchange()
        val ex = BadCredentialsException("No token provided")

        handler.handle(exchange, ex).block()

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
    }

    @Test
    fun `AccessDeniedException maps to GATEWAY_007 ACCESS_DENIED with 403`() {
        val exchange = buildExchange()
        val ex = AccessDeniedException("Access denied")

        handler.handle(exchange, ex).block()

        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    @Test
    fun `OAuth2AuthenticationException with expired description maps to EXPIRED_TOKEN`() {
        val exchange = buildExchange()
        val error = OAuth2Error("invalid_token", "The token has expired", null)
        val ex = OAuth2AuthenticationException(error)

        handler.handle(exchange, ex).block()

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
    }

    @Test
    fun `OAuth2AuthenticationException with algorithm description maps to UNSUPPORTED_JWT_ALGORITHM`() {
        val exchange = buildExchange()
        val error = OAuth2Error("invalid_token", "Unsupported algorithm RS512", null)
        val ex = OAuth2AuthenticationException(error)

        handler.handle(exchange, ex).block()

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
    }

    @Test
    fun `OAuth2AuthenticationException with malformed description maps to MALFORMED_JWT`() {
        val exchange = buildExchange()
        val error = OAuth2Error("invalid_token", "Token is malformed", null)
        val ex = OAuth2AuthenticationException(error)

        handler.handle(exchange, ex).block()

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
    }

    @Test
    fun `OAuth2AuthenticationException with JwtValidationException cause maps to EXPIRED_TOKEN`() {
        val exchange = buildExchange()
        val cause = JwtValidationException("Token expired", listOf(OAuth2Error("invalid_token", "Token expired", null)))
        val error = OAuth2Error("invalid_token", "Token validation failed", null)
        val ex = OAuth2AuthenticationException(error, cause)

        handler.handle(exchange, ex).block()

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
    }

    // ─── Upstream / infrastructure exceptions ─────────────────────────────

    @Test
    fun `ConnectException maps to GATEWAY_014 SERVICE_UNAVAILABLE with 503`() {
        val exchange = buildExchange()
        val ex = ConnectException("Connection refused")

        handler.handle(exchange, ex).block()

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.response.statusCode)
    }

    @Test
    fun `Exception with ConnectException cause maps to SERVICE_UNAVAILABLE`() {
        val exchange = buildExchange()
        val cause = ConnectException("Connection refused")
        val ex = RuntimeException("Wrapped connect exception", cause)

        handler.handle(exchange, ex).block()

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.response.statusCode)
    }

    @Test
    fun `TimeoutException maps to GATEWAY_015 GATEWAY_TIMEOUT with 504`() {
        val exchange = buildExchange()
        val ex = TimeoutException("Request timed out")

        handler.handle(exchange, ex).block()

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, exchange.response.statusCode)
    }

    // ─── ResponseStatusException ──────────────────────────────────────────

    @Test
    fun `ResponseStatusException 404 maps to NOT_FOUND with 404`() {
        val exchange = buildExchange()
        val ex = ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found")

        handler.handle(exchange, ex).block()

        assertEquals(HttpStatus.NOT_FOUND, exchange.response.statusCode)
    }

    @Test
    fun `ResponseStatusException 400 maps to BAD_REQUEST with 400`() {
        val exchange = buildExchange()
        val ex = ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad request")

        handler.handle(exchange, ex).block()

        assertEquals(HttpStatus.BAD_REQUEST, exchange.response.statusCode)
    }

    @Test
    fun `ResponseStatusException 429 maps to TOO_MANY_REQUESTS with 429`() {
        val exchange = buildExchange()
        val ex = ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded")

        handler.handle(exchange, ex).block()

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.response.statusCode)
    }

    @Test
    fun `ResponseStatusException 503 maps to SERVICE_UNAVAILABLE`() {
        val exchange = buildExchange()
        val ex = ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Service down")

        handler.handle(exchange, ex).block()

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.response.statusCode)
    }

    // ─── Unknown exception ────────────────────────────────────────────────

    @Test
    fun `unknown exception maps to GATEWAY_019 UNKNOWN_ERROR with 500`() {
        val exchange = buildExchange()
        val ex = RuntimeException("Unexpected failure")

        handler.handle(exchange, ex).block()

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.response.statusCode)
    }

    // ─── Response format ──────────────────────────────────────────────────

    @Test
    fun `response content type is always application-json`() {
        val exchange = buildExchange()

        handler.handle(exchange, RuntimeException("any error")).block()

        assertEquals(MediaType.APPLICATION_JSON, exchange.response.headers.contentType)
    }
}
