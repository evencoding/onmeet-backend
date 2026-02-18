package com.onmeet.auth.service

import com.onmeet.auth.dto.TokenResponse
import com.onmeet.auth.entity.RefreshToken
import com.onmeet.auth.exception.InvalidTokenException
import com.onmeet.auth.exception.UserNotFoundException
import com.onmeet.auth.repository.jpa.UserRepository
import com.onmeet.auth.repository.redis.RefreshTokenRepository
import com.onmeet.auth.security.JwtTokenProvider
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*
import java.util.concurrent.TimeUnit

@Service
class TokenServiceImpl(
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userRepository: UserRepository,
    private val redisTemplate: StringRedisTemplate
) : TokenService {

    @Transactional
    override fun issueTokens(authentication: Authentication, email: String): TokenResponse {
        val accessToken = jwtTokenProvider.generateToken(authentication)
        val refreshTokenStr = UUID.randomUUID().toString()
        val authorities = authentication.authorities.joinToString(",") { it.authority }

        val refreshToken = RefreshToken(
            mobileOrEmail = email,
            token = refreshTokenStr,
            authority = authorities
        )
        refreshTokenRepository.save(refreshToken)

        return TokenResponse(accessToken, refreshTokenStr)
    }

    @Transactional
    override fun issueGuestTokens(name: String, meetingId: String?): TokenResponse {
        val accessToken = jwtTokenProvider.generateGuestToken(name, listOf("ROLE_GUEST"), meetingId)
        val refreshTokenStr = UUID.randomUUID().toString()
        val authorities = "ROLE_GUEST"

        val refreshToken = RefreshToken(
            mobileOrEmail = name,
            token = refreshTokenStr,
            authority = authorities,
            expiration = 86400L // 1 day
        )
        refreshTokenRepository.save(refreshToken)

        return TokenResponse(accessToken, refreshTokenStr)
    }

    @Transactional
    override fun refreshTokens(token: String): TokenResponse {
        val refreshTokenEntity = refreshTokenRepository.findByToken(token)
            ?: throw InvalidTokenException("Invalid refresh token")

        refreshTokenRepository.delete(refreshTokenEntity)

        val user = userRepository.findByEmail(refreshTokenEntity.mobileOrEmail)
            .orElseThrow { UserNotFoundException("User not found: ${refreshTokenEntity.mobileOrEmail}") }

        val authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
        val newAccessToken = jwtTokenProvider.generateToken(authentication)

        val newRefreshTokenStr = UUID.randomUUID().toString()
        val newRefreshTokenEntity = RefreshToken(
            mobileOrEmail = user.email,
            token = newRefreshTokenStr,
            authority = refreshTokenEntity.authority
        )
        refreshTokenRepository.save(newRefreshTokenEntity)

        return TokenResponse(newAccessToken, newRefreshTokenStr)
    }

    override fun revokeTokens(accessToken: String?, email: String?) {
        accessToken?.takeIf { it.isNotBlank() }?.let { token ->
            jwtTokenProvider.getRemainingTime(token).takeIf { it > 0 }?.let { remainingTime ->
                redisTemplate.opsForValue().set("blacklist:$token", "logout", remainingTime, TimeUnit.MILLISECONDS)
            }
        }
        email?.let { refreshTokenRepository.deleteById(it) }
    }
}
