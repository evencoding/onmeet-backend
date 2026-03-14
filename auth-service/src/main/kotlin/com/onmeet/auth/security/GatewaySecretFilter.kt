package com.onmeet.auth.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

@Component
class GatewaySecretFilter(
    @Value("\${gateway.shared-secret}") private val gatewaySharedSecret: String
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val secret = request.getHeader("X-Gateway-Secret") ?: ""
        val isValid = MessageDigest.isEqual(
            secret.toByteArray(Charsets.UTF_8),
            gatewaySharedSecret.toByteArray(Charsets.UTF_8)
        )

        if (!isValid) {
            response.status = HttpStatus.FORBIDDEN.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write("""{"error":"Forbidden","message":"Invalid or missing gateway secret"}""")
            return
        }

        filterChain.doFilter(request, response)
    }
}
