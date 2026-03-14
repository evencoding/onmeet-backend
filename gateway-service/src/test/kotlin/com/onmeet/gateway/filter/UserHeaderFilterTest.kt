package com.onmeet.gateway.filter

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.time.Instant

/**
 * UserHeaderFilter 단위 테스트.
 * JWT 인증 토큰 존재 여부에 따라 X-User-* 헤더가 올바르게 주입되는지 검증한다.
 */
class UserHeaderFilterTest {

    private val filter = UserHeaderFilter()

    private fun buildJwt(
        userId: String = "42",
        email: String = "user@onmeet.io"
    ): Jwt {
        return Jwt.withTokenValue("test.jwt.token")
            .header("alg", "RS256")
            .claim("userId", userId)
            .subject(email)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
    }

    private fun captureExchangeFromChain(): Pair<GatewayFilterChain, () -> ServerWebExchange?> {
        var captured: ServerWebExchange? = null
        val chain = GatewayFilterChain { ex ->
            captured = ex
            Mono.empty()
        }
        return chain to { captured }
    }

    @Test
    fun `filter injects X-User-Id and X-User-Email from JWT claims`() {
        val jwt = buildJwt(userId = "99", email = "alice@onmeet.io")
        val jwtAuth = JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("ROLE_USER")))

        val request = MockServerHttpRequest.get("/api/teams").build()
        val exchange = MockServerWebExchange.from(request)

        val (chain, getCaptured) = captureExchangeFromChain()

        val config = UserHeaderFilter.Config()
        filter.apply(config)
            .filter(exchange, chain)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(jwtAuth))
            .block()

        val capturedExchange = getCaptured()
        assertNotNull(capturedExchange)
        assertEquals("99", capturedExchange!!.request.headers.getFirst("X-User-Id"))
        assertEquals("alice@onmeet.io", capturedExchange.request.headers.getFirst("X-User-Email"))
    }

    @Test
    fun `filter injects X-User-Roles from JWT authorities`() {
        val jwt = buildJwt()
        val jwtAuth = JwtAuthenticationToken(
            jwt,
            listOf(SimpleGrantedAuthority("ROLE_USER"), SimpleGrantedAuthority("ROLE_ADMIN"))
        )

        val request = MockServerHttpRequest.get("/api/admin").build()
        val exchange = MockServerWebExchange.from(request)

        val (chain, getCaptured) = captureExchangeFromChain()

        filter.apply(UserHeaderFilter.Config())
            .filter(exchange, chain)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(jwtAuth))
            .block()

        val rolesHeader = getCaptured()!!.request.headers.getFirst("X-User-Roles")
        assertNotNull(rolesHeader)
        assertTrue(rolesHeader!!.contains("ROLE_USER"))
        assertTrue(rolesHeader.contains("ROLE_ADMIN"))
    }

    @Test
    fun `filter falls back to JWT subject when userId claim is absent`() {
        val jwt = Jwt.withTokenValue("no-userid-claim")
            .header("alg", "RS256")
            .subject("fallback@onmeet.io")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
        val jwtAuth = JwtAuthenticationToken(jwt, emptyList())

        val request = MockServerHttpRequest.get("/api/profile").build()
        val exchange = MockServerWebExchange.from(request)

        val (chain, getCaptured) = captureExchangeFromChain()

        filter.apply(UserHeaderFilter.Config())
            .filter(exchange, chain)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(jwtAuth))
            .block()

        // Should fall back to JWT subject
        assertEquals("fallback@onmeet.io", getCaptured()!!.request.headers.getFirst("X-User-Id"))
    }

    @Test
    fun `filter passes exchange unmodified when no security context`() {
        val request = MockServerHttpRequest.get("/api/teams").build()
        val exchange = MockServerWebExchange.from(request)

        val (chain, getCaptured) = captureExchangeFromChain()

        filter.apply(UserHeaderFilter.Config())
            .filter(exchange, chain)
            .block()

        // Exchange should pass through as-is (no user headers injected)
        val capturedExchange = getCaptured()
        assertNotNull(capturedExchange)
        assertNull(capturedExchange!!.request.headers.getFirst("X-User-Id"))
        assertNull(capturedExchange.request.headers.getFirst("X-User-Email"))
    }

    @Test
    fun `filter passes exchange unmodified when authentication is not JWT type`() {
        val nonJwtAuth = UsernamePasswordAuthenticationToken("user", "password")
        val context = SecurityContextImpl(nonJwtAuth)

        val request = MockServerHttpRequest.get("/api/data").build()
        val exchange = MockServerWebExchange.from(request)

        val (chain, getCaptured) = captureExchangeFromChain()

        filter.apply(UserHeaderFilter.Config())
            .filter(exchange, chain)
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)))
            .block()

        assertNull(getCaptured()!!.request.headers.getFirst("X-User-Id"))
    }
}
