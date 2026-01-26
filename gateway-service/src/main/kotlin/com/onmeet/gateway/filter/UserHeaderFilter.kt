package com.onmeet.gateway.filter

import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory
import org.springframework.http.server.reactive.ServerHttpRequestDecorator
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

@Component
class UserHeaderFilter : AbstractGatewayFilterFactory<UserHeaderFilter.Config>(Config::class.java) {

    class Config

    override fun apply(config: Config): GatewayFilter {
        return GatewayFilter { exchange, chain ->
            ReactiveSecurityContextHolder.getContext()
                .map { it.authentication }
                .filter { it is JwtAuthenticationToken }
                .map { it as JwtAuthenticationToken }
                .map { jwt ->
                    val userId = jwt.token.claims["userId"]?.toString() ?: jwt.token.subject
                    // Removed sensitive log
                    
                    // PREVENT SPOOFING: Explicitly remove any user-supplied headers first
                    val request = exchange.request.mutate()
                        .headers { httpHeaders ->
                            httpHeaders.remove("X-User-Id")
                            httpHeaders.remove("X-User-Email")
                            httpHeaders.remove("X-User-Roles")
                        }
                        .header("X-User-Id", userId)
                        .header("X-User-Email", jwt.token.subject) 
                        .header("X-User-Roles", jwt.authorities.joinToString(",") { it.authority })
                        .build()
                    exchange.mutate().request(request).build()
                }
                .defaultIfEmpty(exchange)
                .flatMap { chain.filter(it) }
        }
    }
}
