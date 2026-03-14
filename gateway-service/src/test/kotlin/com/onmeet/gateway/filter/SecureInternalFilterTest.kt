package com.onmeet.gateway.filter

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.core.Ordered
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * SecureInternalFilter 단위 테스트.
 * X-Gateway-Secret 헤더가 다운스트림 요청에 올바르게 추가되는지 검증한다.
 */
class SecureInternalFilterTest {

    private val sharedSecret = "test-gateway-shared-secret"
    private val filter = SecureInternalFilter(sharedSecret)

    private fun captureExchangeFromChain(): Pair<GatewayFilterChain, () -> ServerWebExchange?> {
        var captured: ServerWebExchange? = null
        val chain = GatewayFilterChain { ex ->
            captured = ex
            Mono.empty()
        }
        return chain to { captured }
    }

    @Test
    fun `filter adds X-Gateway-Secret header to downstream request`() {
        val request = MockServerHttpRequest.get("/api/users").build()
        val exchange = MockServerWebExchange.from(request)

        val (chain, getCaptured) = captureExchangeFromChain()

        filter.filter(exchange, chain).block()

        val capturedExchange = getCaptured()
        assertNotNull(capturedExchange)
        assertEquals(sharedSecret, capturedExchange!!.request.headers.getFirst("X-Gateway-Secret"))
    }

    @Test
    fun `filter adds X-Gateway-Secret to all types of requests`() {
        listOf("/api/teams", "/auth/v1/users", "/video/v1/rooms").forEach { path ->
            val request = MockServerHttpRequest.get(path).build()
            val exchange = MockServerWebExchange.from(request)

            val (chain, getCaptured) = captureExchangeFromChain()

            filter.filter(exchange, chain).block()

            assertEquals(
                sharedSecret,
                getCaptured()!!.request.headers.getFirst("X-Gateway-Secret"),
                "Expected X-Gateway-Secret to be added for path: $path"
            )
        }
    }

    @Test
    fun `filter preserves existing request headers`() {
        val request = MockServerHttpRequest.get("/api/test")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .build()
        val exchange = MockServerWebExchange.from(request)

        val (chain, getCaptured) = captureExchangeFromChain()

        filter.filter(exchange, chain).block()

        val capturedHeaders = getCaptured()!!.request.headers
        assertEquals("application/json", capturedHeaders.getFirst("Content-Type"))
        assertEquals("application/json", capturedHeaders.getFirst("Accept"))
        assertEquals(sharedSecret, capturedHeaders.getFirst("X-Gateway-Secret"))
    }

    @Test
    fun `filter has highest precedence order`() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE, filter.order)
    }

    @Test
    fun `filter chain is called exactly once`() {
        val request = MockServerHttpRequest.get("/api/data").build()
        val exchange = MockServerWebExchange.from(request)

        var chainCallCount = 0
        val chain = GatewayFilterChain { ex ->
            chainCallCount++
            Mono.empty()
        }

        filter.filter(exchange, chain).block()

        assertEquals(1, chainCallCount)
    }
}
