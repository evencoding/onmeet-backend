package com.onmeet.auth.service

import com.onmeet.auth.dto.GuestLoginRequest
import com.onmeet.auth.dto.LoginRequest
import com.onmeet.auth.dto.TokenResponse
import com.onmeet.auth.repository.jpa.UserRepository
import com.onmeet.common.dto.NotificationRequestDto
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthenticationService(
    private val tokenService: TokenService,
    private val authenticationManager: AuthenticationManager,
    private val userRepository: UserRepository,
    private val notificationEventPublisher: NotificationEventPublisher
) {
    private val log = LoggerFactory.getLogger(AuthenticationService::class.java)

    @Transactional
    fun login(request: LoginRequest): TokenResponse {
        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.email, request.password)
        )

        request.deviceToken?.let { token ->
            userRepository.findByEmail(request.email).ifPresent { user ->
                user.fcmDeviceToken = token
                userRepository.save(user)
                log.info("FCM device token updated for user: ${request.email}")
                notificationEventPublisher.publishNotification(
                    NotificationRequestDto(
                        userId = user.id,
                        type = "SYSTEM",
                        title = "새로운 기기 로그인",
                        body = "새로운 기기에서 로그인 시도가 감지되었습니다."
                    )
                )
            }
        }

        return tokenService.issueTokens(authentication, request.email)
    }

    @Transactional
    fun guestLogin(request: GuestLoginRequest): TokenResponse =
        tokenService.issueGuestTokens(request.name, request.meetingId)

    @Transactional
    fun refresh(token: String): TokenResponse =
        tokenService.refreshTokens(token)

    fun logout(accessToken: String?, email: String?) {
        tokenService.revokeTokens(accessToken, email)
    }
}
