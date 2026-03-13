package com.onmeet.gateway.config

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Configuration
class GatewayConfig {

    @Bean
    fun ipKeyResolver(): KeyResolver {
        return KeyResolver { exchange: ServerWebExchange ->
            exchange.getPrincipal<java.security.Principal>()
                .map { it.name }
                .switchIfEmpty(resolveClientIp(exchange))
        }
    }

    private fun resolveClientIp(exchange: ServerWebExchange): Mono<String> {
        val ip = exchange.request.headers.getFirst("X-Forwarded-For")
            ?.split(",")?.firstOrNull()?.trim()
            ?: exchange.request.remoteAddress?.address?.hostAddress
        // Returning Mono.empty() causes the rate limiter to deny the request (deny-empty-key=true by default)
        return if (ip != null) Mono.just(ip) else Mono.empty()
    }
}
