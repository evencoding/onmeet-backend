package com.onmeet.gateway.filter

import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequestDecorator
import org.springframework.stereotype.Component

@Component
class TokenRelayFilter : AbstractGatewayFilterFactory<TokenRelayFilter.Config>(Config::class.java) {

    private val logger = LoggerFactory.getLogger(TokenRelayFilter::class.java)

    class Config

    override fun apply(config: Config): GatewayFilter {
        return GatewayFilter { exchange, chain ->
            val request = exchange.request
            
            // Allow public auth endpoints pass-through without token check
            val path = request.uri.path
            logger.debug("Processing request path: $path")

            if (path.startsWith("/auth/") ||
                path.startsWith("/.well-known")) {
               return@GatewayFilter chain.filter(exchange)
            }

            // Extract Access Token from Cookie
            val cookies = request.cookies
            logger.debug("Available Cookies for $path: ${cookies.keys}")
            
            val accessTokenCookie = cookies.getFirst("accessToken")
            
            if (accessTokenCookie == null) {
                logger.debug("Missing accessToken cookie. Full cookie map keys: ${cookies.keys}")
                exchange.response.statusCode = HttpStatus.UNAUTHORIZED
                return@GatewayFilter exchange.response.setComplete()
            }

            val token = accessTokenCookie.value

            // Fix for ReadOnlyHttpHeaders: Use ServerHttpRequestDecorator
            val modifiedRequest = object : ServerHttpRequestDecorator(request) {
                override fun getHeaders(): HttpHeaders {
                    val headers = HttpHeaders()
                    headers.putAll(super.getHeaders())
                    headers.add("Authorization", "Bearer $token")
                    return headers
                }
            }

            return@GatewayFilter chain.filter(exchange.mutate().request(modifiedRequest).build())
        }
    }
}
