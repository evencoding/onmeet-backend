package com.onmeet.auth.security

import com.onmeet.common.security.GatewayPreAuthFilter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class AuthGatewayPreAuthFilter(
    @Value("\${gateway.shared-secret}") gatewaySharedSecret: String
) : GatewayPreAuthFilter(gatewaySharedSecret) {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        // Allow public endpoints (specifically JWKS and Actuator) to bypass secret validation
        return path == "/.well-known/jwks.json" || 
               path.startsWith("/.well-known/jwks.json/") ||
               path.startsWith("/auth/actuator/")
    }
}