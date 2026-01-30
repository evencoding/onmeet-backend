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
        
        val request = exchange.request.mutate()
            .header("X-Gateway-Secret", gatewaySharedSecret)
            .build()
        
        return chain.filter(exchange.mutate().request(request).build())
    }

    override fun getOrder(): Int {
        return Ordered.HIGHEST_PRECEDENCE
    }
}
