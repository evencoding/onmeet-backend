package com.onmeet.gateway.config

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.Optional

@Configuration
class GatewayConfig {

    @Bean
    fun ipKeyResolver(): KeyResolver {
        return KeyResolver { exchange: ServerWebExchange ->
            // Use Principal Name if available, otherwise use IP address
            exchange.getPrincipal<java.security.Principal>()
                .map { it.name }
                .defaultIfEmpty(
                    Optional.ofNullable(exchange.request.remoteAddress)
                        .map { it.address.hostAddress }
                        .orElse("unknown")
                )
        }
    }
}
