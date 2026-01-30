package com.onmeet.auth.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest
import java.nio.charset.StandardCharsets

@Component
class GatewayPreAuthFilter(
    @Value("\${gateway.shared-secret}") private val gatewaySharedSecret: String
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(GatewayPreAuthFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path = request.requestURI
        // Allow public endpoints (specifically JWKS) to bypass secret validation
        if (path == "/.well-known/jwks.json" || path.startsWith("/.well-known/jwks.json/")) {
            filterChain.doFilter(request, response)
            return
        }

        val gatewaySecret = request.getHeader("X-Gateway-Secret")

        if (gatewaySecret == null) {
            log.warn("Missing X-Gateway-Secret header for request: ${request.requestURI}")
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Missing Gateway Secret")
            return
        }

        if (!MessageDigest.isEqual(
                gatewaySecret.toByteArray(StandardCharsets.UTF_8),
                gatewaySharedSecret.toByteArray(StandardCharsets.UTF_8)
            )
        ) {
            log.warn("Invalid X-Gateway-Secret header for request: ${request.requestURI}. Received: $gatewaySecret")
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid Gateway Secret")
            return
        }

        log.debug("Gateway Secret validated successfully for request: ${request.requestURI}")

        val userId = request.getHeader("X-User-Id")
        val userRoles = request.getHeader("X-User-Roles")

        if (!userId.isNullOrBlank()) {
            val authorities = if (!userRoles.isNullOrBlank()) {
                userRoles.split(",")
                    .map { org.springframework.security.core.authority.SimpleGrantedAuthority(it.trim()) }
            } else {
                emptyList()
            }

            log.debug("Setting security context for user: $userId with roles: $userRoles")
            val auth = org.springframework.security.authentication.UsernamePasswordAuthenticationToken(userId, null, authorities)
            org.springframework.security.core.context.SecurityContextHolder.getContext().authentication = auth
        }

        filterChain.doFilter(request, response)
    }
}
