package com.onmeet.gateway.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.onmeet.gateway.exception.GlobalErrorWebExceptionHandler
import com.onmeet.gateway.filter.SecureInternalFilter
import com.onmeet.gateway.filter.UserHeaderFilter
import com.onmeet.gateway.security.CookieServerAuthenticationConverter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.http.HttpCookie
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.net.ConnectException
import java.time.Instant

/**
 * Gateway 통합 테스트.
 * 필터 체인과 에러 핸들러가 함께 올바르게 동작하는지 검증한다.
 */
class GatewayIntegrationTest {

    private val sharedSecret = "integration-test-secret"
    private val objectMapper = ObjectMapper()

    private val secureInternalFilter = SecureInternalFilter(sharedSecret)
    private val userHeaderFilter = UserHeaderFilter()
    private val errorHandler = GlobalErrorWebExceptionHandler(objectMapper)
    private val cookieConverter = CookieServerAuthenticationConverter()

    private fun buildJwt(userId: String = "10", email: String = "test@onmeet.io"): Jwt {
        return Jwt.withTokenValue("integration.jwt.token")
            .header("alg", "RS256")
            .claim("userId", userId)
            .subject(email)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
    }

    // ─── SecureInternalFilter + UserHeaderFilter cooperation ─────────────

    @Test
    fun `SecureInternalFilter and UserHeaderFilter both apply to authenticated request`() {
        val jwt = buildJwt(userId = "55", email = "bob@onmeet.io")
        val jwtAuth = JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("ROLE_USER")))

        val request = MockServerHttpRequest.get("/api/teams/1").build()
        val exchange = MockServerWebExchange.from(request)

        var afterSecure: ServerWebExchange? = null
        var afterUserHeader: ServerWebExchange? = null

        // Step 1: SecureInternalFilter
        val secureChain = GatewayFilterChain { ex ->
            afterSecure = ex
            Mono.empty()
        }
        secureInternalFilter.filter(exchange, secureChain).block()

        assertNotNull(afterSecure)
        assertEquals(sharedSecret, afterSecure!!.request.headers.getFirst("X-Gateway-Secret"))

        // Step 2: UserHeaderFilter on the same original exchange (simulating chain)
        val userHeaderChain = GatewayFilterChain { ex ->
            afterUserHeader = ex
            Mono.empty()
        }

        userHeaderFilter.apply(UserHeaderFilter.Config())
            .filter(exchange, userHeaderChain)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(jwtAuth))
            .block()

        assertNotNull(afterUserHeader)
        assertEquals("55", afterUserHeader!!.request.headers.getFirst("X-User-Id"))
        assertEquals("bob@onmeet.io", afterUserHeader!!.request.headers.getFirst("X-User-Email"))
    }

    // ─── CookieServerAuthenticationConverter integration ─────────────────

    @Test
    fun `CookieConverter extracts token and it can be used to create JwtAuthToken`() {
        val tokenValue = "eyJhbGciOiJSUzI1NiJ9.payload.sig"
        val request = MockServerHttpRequest.get("/api/users")
            .cookie(HttpCookie("accessToken", tokenValue))
            .build()
        val exchange = MockServerWebExchange.from(request)

        val authToken = cookieConverter.convert(exchange).block()

        assertNotNull(authToken)
        // The token can be cast to BearerTokenAuthenticationToken and used for JWT validation
        assertEquals(tokenValue, authToken!!.credentials.toString())
    }

    @Test
    fun `unauthenticated request has no cookie token`() {
        val request = MockServerHttpRequest.get("/api/secret-data").build()
        val exchange = MockServerWebExchange.from(request)

        val authToken = cookieConverter.convert(exchange).blockOptional()

        assertTrue(authToken.isEmpty, "Request without cookie should produce no authentication token")
    }

    // ─── Error handler integration ────────────────────────────────────────

    @Test
    fun `service unavailable error is handled with 503 status`() {
        val request = MockServerHttpRequest.get("/api/video/rooms").build()
        val exchange = MockServerWebExchange.from(request)

        errorHandler.handle(exchange, ConnectException("video-service:8083 - Connection refused")).block()

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.response.statusCode)
    }

    @Test
    fun `error handler writes JSON content type`() {
        val request = MockServerHttpRequest.get("/api/ai/summary").build()
        val exchange = MockServerWebExchange.from(request)

        errorHandler.handle(exchange, RuntimeException("AI service failed")).block()

        assertEquals("application/json", exchange.response.headers.contentType?.toString())
    }

    // ─── Path-based authentication logic (CookieConverter) ───────────────

    @Test
    fun `public path request without cookie returns empty auth token`() {
        // Simulates a call to /auth/v1/login (public) without cookie
        val request = MockServerHttpRequest.post("/auth/v1/login").build()
        val exchange = MockServerWebExchange.from(request)

        val auth = cookieConverter.convert(exchange).blockOptional()

        assertTrue(auth.isEmpty)
    }

    @Test
    fun `protected path request with cookie returns auth token`() {
        val request = MockServerHttpRequest.get("/api/notification/v1/subscribe")
            .cookie(HttpCookie("accessToken", "valid.jwt.here"))
            .build()
        val exchange = MockServerWebExchange.from(request)

        val auth = cookieConverter.convert(exchange).block()

        assertNotNull(auth)
    }
}
