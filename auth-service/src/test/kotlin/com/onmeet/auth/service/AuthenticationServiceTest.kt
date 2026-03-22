package com.onmeet.auth.service

import com.onmeet.auth.dto.GuestLoginRequest
import com.onmeet.auth.dto.LoginRequest
import com.onmeet.auth.dto.TokenResponse
import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.User
import com.onmeet.auth.repository.jpa.UserRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import java.util.Optional

@ExtendWith(MockKExtension::class)
class AuthenticationServiceTest {

    @MockK
    private lateinit var tokenService: TokenService

    @MockK
    private lateinit var authenticationManager: AuthenticationManager

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var notificationEventPublisher: NotificationEventPublisher

    @InjectMockKs
    private lateinit var authenticationService: AuthenticationService

    private val company = Company(id = 1L, name = "Test Company")
    private val user = User(
        id = 1L,
        email = "test@test.com",
        passwordHash = "hash",
        name = "Test User",
        roles = mutableSetOf(User.Role.USER),
        company = company,
        status = User.UserStatus.ACTIVE
    )

    @Test
    fun `login should return token response`() {
        // given
        val request = LoginRequest("test@test.com", "password")
        val authentication = mockk<Authentication>()
        val tokenResponse = TokenResponse("access_token", "refresh_token")

        every { authenticationManager.authenticate(any()) } returns authentication
        every { tokenService.issueTokens(authentication, "test@test.com") } returns tokenResponse

        // when
        val result = authenticationService.login(request)

        // then
        assertEquals("access_token", result.accessToken)
        assertEquals("refresh_token", result.refreshToken)
        verify { authenticationManager.authenticate(any<UsernamePasswordAuthenticationToken>()) }
        verify { tokenService.issueTokens(authentication, "test@test.com") }
    }

    @Test
    fun `login should save FCM device token when provided`() {
        // given
        val request = LoginRequest("test@test.com", "password", deviceToken = "fcm-token-123")
        val authentication = mockk<Authentication>()
        val tokenResponse = TokenResponse("access_token", "refresh_token")

        every { authenticationManager.authenticate(any()) } returns authentication
        every { userRepository.findByEmail("test@test.com") } returns Optional.of(user)
        every { userRepository.save(any()) } returns user
        every { notificationEventPublisher.publishNotification(any()) } returns Unit
        every { tokenService.issueTokens(authentication, "test@test.com") } returns tokenResponse

        // when
        authenticationService.login(request)

        // then
        assertEquals("fcm-token-123", user.fcmDeviceToken)
        verify { userRepository.save(match { it.fcmDeviceToken == "fcm-token-123" }) }
    }

    @Test
    fun `login should not update FCM token when deviceToken is not provided`() {
        // given
        val request = LoginRequest("test@test.com", "password", deviceToken = null)
        val authentication = mockk<Authentication>()
        val tokenResponse = TokenResponse("access_token", "refresh_token")

        every { authenticationManager.authenticate(any()) } returns authentication
        every { tokenService.issueTokens(authentication, "test@test.com") } returns tokenResponse

        // when
        authenticationService.login(request)

        // then
        verify(exactly = 0) { userRepository.findByEmail(any()) }
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `guestLogin should issue guest tokens`() {
        // given
        val request = GuestLoginRequest(name = "Guest User", meetingId = "meeting-123")
        val tokenResponse = TokenResponse("guest_access", "guest_refresh")

        every { tokenService.issueGuestTokens("Guest User", "meeting-123") } returns tokenResponse

        // when
        val result = authenticationService.guestLogin(request)

        // then
        assertEquals("guest_access", result.accessToken)
        verify { tokenService.issueGuestTokens("Guest User", "meeting-123") }
    }

    @Test
    fun `guestLogin should work without meeting id`() {
        // given
        val request = GuestLoginRequest(name = "Guest", meetingId = null)
        val tokenResponse = TokenResponse("guest_access", "guest_refresh")

        every { tokenService.issueGuestTokens("Guest", null) } returns tokenResponse

        // when
        val result = authenticationService.guestLogin(request)

        // then
        assertEquals("guest_access", result.accessToken)
    }

    @Test
    fun `refresh should delegate to tokenService`() {
        // given
        val tokenResponse = TokenResponse("new_access", "new_refresh")
        every { tokenService.refreshTokens("old_refresh") } returns tokenResponse

        // when
        val result = authenticationService.refresh("old_refresh")

        // then
        assertEquals("new_access", result.accessToken)
        verify { tokenService.refreshTokens("old_refresh") }
    }

    @Test
    fun `logout should revoke tokens via tokenService`() {
        // given
        every { tokenService.revokeTokens("access_token", "test@test.com") } returns Unit

        // when
        authenticationService.logout("access_token", "test@test.com")

        // then
        verify { tokenService.revokeTokens("access_token", "test@test.com") }
    }
}
