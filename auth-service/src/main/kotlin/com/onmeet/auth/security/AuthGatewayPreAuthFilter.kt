package com.onmeet.auth.security

import com.onmeet.auth.config.GatewayProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

@Component
class AuthGatewayPreAuthFilter(
    private val gatewayProperties: GatewayProperties,
    @org.springframework.beans.factory.annotation.Value("\${server.servlet.context-path:}") private val contextPath: String
) : OncePerRequestFilter() {

    private val allowedPaths by lazy {
        setOf(
            "/.well-known/jwks.json",
            "$contextPath/.well-known/jwks.json",
            "$contextPath/v1/.well-known/jwks.json",
            "$contextPath/actuator/health",
            "$contextPath/v1/register/company",
            "$contextPath/v1/register/join",
            "$contextPath/v1/invitations/validate",
            "$contextPath/v1/login",
            "$contextPath/v1/login/guest",
            "$contextPath/v1/refresh",
            "$contextPath/v1/logout",
            "$contextPath/v1/check",
            // legacy paths (without /v1) for backward compatibility if any
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
        val path = request.requestURI
        // Swagger / OpenAPI docs bypass
        if (path.contains("/v3/api-docs") || path.contains("/api-docs") ||
            path.contains("/swagger-ui") || path.endsWith("/doc.json") ||
            path.startsWith("$contextPath/actuator/")) {
            return true
        }
        // If already authenticated (e.g. via JWT), skip gateway secret check
        if (org.springframework.security.core.context.SecurityContextHolder.getContext().authentication != null) {
            return true
        }
        return allowedPaths.contains(path)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // Validate Gateway Shared Secret to prevent spoofing
        val gatewaySecret = request.getHeader("X-Gateway-Secret") ?: ""
        if (!MessageDigest.isEqual(
                gatewaySecret.toByteArray(Charsets.UTF_8),
                gatewayProperties.sharedSecret.toByteArray(Charsets.UTF_8)
            )
        ) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid Gateway Secret")
            return
        }

        // DO NOT inject X-User-Id into SecurityContext
        // Auth service uses JwtAuthenticationFilter for authentication
        filterChain.doFilter(request, response)
    }
}