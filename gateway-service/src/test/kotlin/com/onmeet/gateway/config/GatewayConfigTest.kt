package com.onmeet.gateway.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.security.Principal
import java.net.InetSocketAddress

class GatewayConfigTest {

    private val gatewayConfig = GatewayConfig()
    private val keyResolver: KeyResolver = gatewayConfig.ipKeyResolver()

    @Test
    fun `should resolve key from principal when present`() {
        // Given
        val request = MockServerHttpRequest.get("/").build()
        val exchange = MockServerWebExchange.builder(request).build()
        
        // Mock Principal
        val principal = Principal { "test-user" }
        val exchangeWithPrincipal = exchange.mutate().principal(Mono.just(principal)).build()

        // When
        val key = keyResolver.resolve(exchangeWithPrincipal).block()

        // Then
        assertEquals("test-user", key)
    }

    @Test
    fun `should resolve key from IP when principal is missing`() {
        // Given
        val address = InetSocketAddress("192.168.0.1", 8080)
        val request = MockServerHttpRequest.get("/").remoteAddress(address).build()
        val exchange = MockServerWebExchange.builder(request).build()

        // When
        val key = keyResolver.resolve(exchange).block()

        // Then
        assertEquals("192.168.0.1", key)
    }
}
