package com.onmeet.auth.security

import com.onmeet.common.security.GatewayPreAuthFilter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class AuthGatewayPreAuthFilter(
    @Value("\${gateway.shared-secret}") gatewaySharedSecret: String
) : GatewayPreAuthFilter(gatewaySharedSecret) {

    private val allowedPaths = setOf(
        "/.well-known/jwks.json",
        "/actuator/health",
        "/auth/signup",
        "/auth/signup/company",
        "/auth/join",
        "/auth/login",
        "/auth/guest/login",
        "/auth/refresh",
        "/auth/logout",
        "/auth/check"
    )

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return allowedPaths.contains(path) || path.startsWith("/actuator/")
    }
}