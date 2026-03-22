package com.onmeet.auth.service

import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.RefreshToken
import com.onmeet.auth.entity.User
import com.onmeet.auth.repository.jpa.UserRepository
import com.onmeet.auth.repository.redis.RefreshTokenRepository
import com.onmeet.auth.security.JwtTokenProvider
import com.onmeet.common.exception.BusinessException
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import java.util.Optional
import java.util.concurrent.TimeUnit

@ExtendWith(MockKExtension::class)
class TokenServiceImplTest {

    @MockK
    private lateinit var jwtTokenProvider: JwtTokenProvider

    @MockK
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var redisTemplate: StringRedisTemplate

    @InjectMockKs
    private lateinit var tokenService: TokenServiceImpl

    private val company = Company(id = 1L, name = "Test Company")
    private val user = User(
        id = 1L,
        email = "test@example.com",
        passwordHash = "hashed",
        name = "Test User",
        roles = mutableSetOf(User.Role.USER),
        company = company,
        status = User.UserStatus.ACTIVE
    )

    @Test
    fun `issueTokens should return access and refresh token`() {
        // given
        val authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
        every { jwtTokenProvider.generateToken(authentication) } returns "access_token"
        every { refreshTokenRepository.save(any()) } returns mockk()

        // when
        val result = tokenService.issueTokens(authentication, "test@example.com")

        // then
        assertEquals("access_token", result.accessToken)
        assertNotNull(result.refreshToken)
        verify { refreshTokenRepository.save(any()) }
    }

    @Test
    fun `issueTokens should save refresh token with email as id`() {
        // given
        val authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
        val slot = slot<RefreshToken>()
        every { jwtTokenProvider.generateToken(authentication) } returns "access_token"
        every { refreshTokenRepository.save(capture(slot)) } returns mockk()

        // when
        tokenService.issueTokens(authentication, "test@example.com")

        // then
        assertEquals("test@example.com", slot.captured.mobileOrEmail)
    }

    @Test
    fun `issueGuestTokens should return tokens with ROLE_GUEST authority`() {
        // given
        val slot = slot<RefreshToken>()
        every { jwtTokenProvider.generateGuestToken("GuestUser", listOf("ROLE_GUEST"), "meeting123") } returns "guest_access_token"
        every { refreshTokenRepository.save(capture(slot)) } returns mockk()

        // when
        val result = tokenService.issueGuestTokens("GuestUser", "meeting123")

        // then
        assertEquals("guest_access_token", result.accessToken)
        assertNotNull(result.refreshToken)
        assertEquals("ROLE_GUEST", slot.captured.authority)
        assertEquals(TokenServiceImpl.GUEST_TOKEN_EXPIRY_SECONDS, slot.captured.expiration)
    }

    @Test
    fun `refreshTokens should return new tokens for valid refresh token`() {
        // given
        val refreshToken = RefreshToken(mobileOrEmail = "test@example.com", token = "old_token", authority = "ROLE_USER")
        every { refreshTokenRepository.findByToken("old_token") } returns refreshToken
        every { refreshTokenRepository.delete(refreshToken) } returns Unit
        every { userRepository.findByEmail("test@example.com") } returns Optional.of(user)
        every { jwtTokenProvider.generateToken(any()) } returns "new_access_token"
        every { refreshTokenRepository.save(any()) } returns mockk()

        // when
        val result = tokenService.refreshTokens("old_token")

        // then
        assertEquals("new_access_token", result.accessToken)
        assertNotNull(result.refreshToken)
        verify { refreshTokenRepository.delete(refreshToken) }
    }

    @Test
    fun `refreshTokens should throw BusinessException for invalid refresh token`() {
        // given
        every { refreshTokenRepository.findByToken("invalid_token") } returns null

        // when & then
        assertThrows<BusinessException> {
            tokenService.refreshTokens("invalid_token")
        }
    }

    @Test
    fun `refreshTokens should throw BusinessException when user not found`() {
        // given
        val refreshToken = RefreshToken(mobileOrEmail = "missing@example.com", token = "some_token", authority = "ROLE_USER")
        every { refreshTokenRepository.findByToken("some_token") } returns refreshToken
        every { refreshTokenRepository.delete(refreshToken) } returns Unit
        every { userRepository.findByEmail("missing@example.com") } returns Optional.empty()

        // when & then
        assertThrows<BusinessException> {
            tokenService.refreshTokens("some_token")
        }
    }

    @Test
    fun `revokeTokens should blacklist access token and delete refresh token`() {
        // given
        val valueOps = mockk<ValueOperations<String, String>>()
        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.set(any(), any(), any(), any()) } returns Unit
        every { jwtTokenProvider.getRemainingTime("valid_access") } returns 3600000L
        every { refreshTokenRepository.deleteById("test@example.com") } returns Unit

        // when
        tokenService.revokeTokens("valid_access", "test@example.com")

        // then
        verify { valueOps.set("blacklist:valid_access", "logout", 3600000L, TimeUnit.MILLISECONDS) }
        verify { refreshTokenRepository.deleteById("test@example.com") }
    }

    @Test
    fun `revokeTokens should skip blacklisting when access token is null`() {
        // given
        every { refreshTokenRepository.deleteById("test@example.com") } returns Unit

        // when
        tokenService.revokeTokens(null, "test@example.com")

        // then
        verify(exactly = 0) { redisTemplate.opsForValue() }
        verify { refreshTokenRepository.deleteById("test@example.com") }
    }

    @Test
    fun `revokeTokens should skip blacklisting when remaining time is 0`() {
        // given
        every { jwtTokenProvider.getRemainingTime("expired_token") } returns 0L
        every { refreshTokenRepository.deleteById("test@example.com") } returns Unit

        // when
        tokenService.revokeTokens("expired_token", "test@example.com")

        // then
        verify(exactly = 0) { redisTemplate.opsForValue() }
        verify { refreshTokenRepository.deleteById("test@example.com") }
    }
}
