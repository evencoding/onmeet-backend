package com.onmeet.auth.service

import com.onmeet.auth.dto.TokenResponse
import org.springframework.security.core.Authentication

interface TokenService {
    fun issueTokens(authentication: Authentication, email: String): TokenResponse
    fun issueGuestTokens(name: String, meetingId: String?): TokenResponse
    fun refreshTokens(token: String): TokenResponse
    fun revokeTokens(accessToken: String?, email: String?)
}
