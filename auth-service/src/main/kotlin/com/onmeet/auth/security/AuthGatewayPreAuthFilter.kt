package com.onmeet.auth.security

import com.onmeet.auth.config.GatewayProperties
import com.onmeet.common.security.GatewayPreAuthFilter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

@Component
class AuthGatewayPreAuthFilter(
    gatewayProperties: GatewayProperties
) : GatewayPreAuthFilter(gatewayProperties.sharedSecret) {

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
        // If already authenticated (e.g. via JWT), skip gateway secret check
        if (org.springframework.security.core.context.SecurityContextHolder.getContext().authentication != null) {
            return true
        }
        
        val path = request.requestURI
        return allowedPaths.contains(path) || path.startsWith("/actuator/")
    }
}