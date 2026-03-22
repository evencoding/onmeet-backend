package com.onmeet.common.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

open class GatewayPreAuthFilter(
    private val gatewaySharedSecret: String
) : OncePerRequestFilter() {

    companion object {
        private val ALLOWED_PATHS = setOf(
            "/actuator/health",
            "/actuator/info"
        )
        // AntPathMatcher-based patterns prevent path bypass attacks like /api/swagger-ui-exploit
        private val EXCLUDED_PATTERNS = listOf(
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
        )
        private val antPathMatcher = AntPathMatcher()
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return ALLOWED_PATHS.contains(path) ||
                path.startsWith("/actuator/") ||
                path.endsWith("/doc.json") ||
                EXCLUDED_PATTERNS.any { antPathMatcher.match(it, path) }
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
                gatewaySharedSecret.toByteArray(Charsets.UTF_8)
            )
        ) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid Gateway Secret")
            return
        }

        val userId = request.getHeader("X-User-Id")
        val userRoles = request.getHeader("X-User-Roles")

        if (!userId.isNullOrBlank()) {
            val authorities = userRoles?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.map { SimpleGrantedAuthority(it) }
                ?: emptyList()

            val auth = UsernamePasswordAuthenticationToken(userId, null, authorities)
            SecurityContextHolder.getContext().authentication = auth
        }

        filterChain.doFilter(request, response)
    }
}
