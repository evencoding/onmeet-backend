package com.onmeet.auth.security

import com.onmeet.auth.config.GatewayProperties
import com.onmeet.common.security.GatewayPreAuthFilter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

@Component
class AuthGatewayPreAuthFilter(
    gatewayProperties: com.onmeet.auth.config.GatewayProperties,
    @org.springframework.beans.factory.annotation.Value("\${server.servlet.context-path:}") private val contextPath: String
) : GatewayPreAuthFilter(gatewayProperties.sharedSecret) {

    private val allowedPaths by lazy {
        setOf(
            "/.well-known/jwks.json",
            "$contextPath/.well-known/jwks.json",
            "$contextPath/actuator/health",
            "$contextPath/register/company",
            "$contextPath/register/join",
            "$contextPath/invitations/validate",
            "$contextPath/login",
            "$contextPath/login/guest",
            "$contextPath/refresh",
            "$contextPath/logout",
            "$contextPath/check"
        )
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        // If already authenticated (e.g. via JWT), skip gateway secret check
        if (org.springframework.security.core.context.SecurityContextHolder.getContext().authentication != null) {
            return true
        }
        
        val path = request.requestURI
        return allowedPaths.contains(path) || path.startsWith("$contextPath/actuator/")
    }
}