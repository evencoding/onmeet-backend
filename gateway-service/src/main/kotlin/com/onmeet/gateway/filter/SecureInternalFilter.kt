package com.onmeet.gateway.filter

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.HttpHeaders
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpRequestDecorator
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
class SecureInternalFilter(
    @Value("\${gateway.shared-secret}") private val gatewaySharedSecret: String
) : GlobalFilter, Ordered {

    private val logger = LoggerFactory.getLogger(SecureInternalFilter::class.java)

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        logger.debug("Applying X-Gateway-Secret to request: ${exchange.request.uri.path}")
        
        val decoratedRequest = object : ServerHttpRequestDecorator(exchange.request) {
            override fun getHeaders(): HttpHeaders {
                val headers = HttpHeaders()
                headers.putAll(super.getHeaders())
                headers.set("X-Gateway-Secret", gatewaySharedSecret)
                // Return mutable headers to allow downstream filters to modify them if needed
                return headers
            }
        }

        return chain.filter(exchange.mutate().request(decoratedRequest).build())
    }

    override fun getOrder(): Int {
        return Ordered.HIGHEST_PRECEDENCE
    }
}
