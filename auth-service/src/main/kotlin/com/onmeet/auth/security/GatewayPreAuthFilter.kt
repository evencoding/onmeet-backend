package com.onmeet.auth.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest
import java.nio.charset.StandardCharsets

@Component
class GatewayPreAuthFilter(
    @Value("\${gateway.shared-secret}") private val gatewaySharedSecret: String
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val gatewaySecret = request.getHeader("X-Gateway-Secret")

        if (gatewaySecret == null || !MessageDigest.isEqual(
                gatewaySecret.toByteArray(StandardCharsets.UTF_8),
                gatewaySharedSecret.toByteArray(StandardCharsets.UTF_8)
            )
        ) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid Gateway Secret")
            return
        }

        filterChain.doFilter(request, response)
    }
}
