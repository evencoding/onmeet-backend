package com.onmeet.auth.security

import com.onmeet.common.security.JwtConstants
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.data.redis.core.StringRedisTemplate

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
    private val redisTemplate: StringRedisTemplate
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = resolveToken(request)

        if (token != null && jwtTokenProvider.validateToken(token)) {
            // Check Blacklist
            if (redisTemplate.hasKey("blacklist:$token") == true) {
                // Token is blacklisted
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token is blacklisted")
                return
            }

            val authentication = jwtTokenProvider.getAuthentication(token)
            SecurityContextHolder.getContext().authentication = authentication
        }

        filterChain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader(JwtConstants.AUTHORIZATION_HEADER)
        if (bearerToken != null && bearerToken.startsWith(JwtConstants.BEARER_PREFIX)) {
            return bearerToken.substring(JwtConstants.BEARER_PREFIX.length)
        }
        return request.cookies?.find { it.name == JwtConstants.ACCESS_TOKEN_COOKIE_NAME }?.value
    }
}
