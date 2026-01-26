package com.onmeet.gateway.filter

import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.http.server.reactive.ServerHttpRequest

@Component
class TokenRelayFilter : AbstractGatewayFilterFactory<TokenRelayFilter.Config>(Config::class.java) {

    private val logger = LoggerFactory.getLogger(TokenRelayFilter::class.java)

    class Config

    override fun apply(config: Config): GatewayFilter {
        return GatewayFilter { exchange, chain ->
            val request = exchange.request
            
            // Allow public auth endpoints pass-through without token check
            // However, token relay might still be useful if they are authenticated, but usually login/signup don't have cookies yet.
            if (request.uri.path.contains("/auth/login") || request.uri.path.contains("/auth/signup")) {
               return@GatewayFilter chain.filter(exchange)
            }

            // Extract Access Token from Cookie
            val accessTokenCookie = request.cookies.getFirst("accessToken")
            
            if (accessTokenCookie == null) {
                logger.error("Missing accessToken cookie")
                exchange.response.statusCode = HttpStatus.UNAUTHORIZED
                return@GatewayFilter exchange.response.setComplete()
            }

            val token = accessTokenCookie.value

            // validate generic JWT structure or signature here if needed (optional at gateway level if optimizing for speed)
            // For rigorous security, we should validate it using the public key.
            // For now, we relay it. The downstream service MUST also validate it or the gateway MUST validate it fully.
            // Requirement says "Gateway relays...", let's mutate the request.

            val modifiedRequest: ServerHttpRequest = request.mutate()
                .header("Authorization", "Bearer \$token")
                .build()

            return@GatewayFilter chain.filter(exchange.mutate().request(modifiedRequest).build())
        }
    }
}
