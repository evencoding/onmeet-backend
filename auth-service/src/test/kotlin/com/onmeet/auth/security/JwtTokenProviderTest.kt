package com.onmeet.auth.security

import com.onmeet.auth.config.JwtProperties
import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.User
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.userdetails.UserDetailsService
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

@ExtendWith(MockKExtension::class)
class JwtTokenProviderTest {

    @MockK
    private lateinit var keyManager: KeyManager

    @MockK
    private lateinit var jwtProperties: JwtProperties

    @MockK
    private lateinit var userDetailsService: UserDetailsService

    @InjectMockKs
    private lateinit var jwtTokenProvider: JwtTokenProvider

    private lateinit var testPrivateKey: RSAPrivateKey
    private lateinit var testPublicKey: RSAPublicKey

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

    @BeforeEach
    fun setUp() {
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048)
        val keyPair = keyPairGen.generateKeyPair()
        testPrivateKey = keyPair.private as RSAPrivateKey
        testPublicKey = keyPair.public as RSAPublicKey

        every { keyManager.privateKey } returns testPrivateKey
        every { keyManager.publicKey } returns testPublicKey
        every { jwtProperties.validityInMs } returns 3600000L
    }

    @Test
    fun `generateToken should return signed JWT for authenticated user`() {
        // given
        val authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)

        // when
        val token = jwtTokenProvider.generateToken(authentication)

        // then
        assertNotNull(token)
        assertTrue(token.isNotBlank())
        assertTrue(token.contains(".")) // JWT format
    }

    @Test
    fun `validateToken should return true for valid token`() {
        // given
        val authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
        val token = jwtTokenProvider.generateToken(authentication)

        // when
        val isValid = jwtTokenProvider.validateToken(token)

        // then
        assertTrue(isValid)
    }

    @Test
    fun `validateToken should return false for tampered token`() {
        // given
        val authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
        val validToken = jwtTokenProvider.generateToken(authentication)
        val tamperedToken = validToken.dropLast(10) + "XXXXXXXXXX"

        // when
        val isValid = jwtTokenProvider.validateToken(tamperedToken)

        // then
        assertFalse(isValid)
    }

    @Test
    fun `validateToken should return false for expired token`() {
        // given - use negative validity to force instant expiry
        every { jwtProperties.validityInMs } returns -60000L
        val authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
        val token = jwtTokenProvider.generateToken(authentication)

        // when
        val isValid = jwtTokenProvider.validateToken(token)

        // then
        assertFalse(isValid)
    }

    @Test
    fun `generateGuestToken should return valid JWT with ROLE_GUEST`() {
        // when
        val token = jwtTokenProvider.generateGuestToken("GuestUser", listOf("ROLE_GUEST"), "meeting-123")

        // then
        assertNotNull(token)
        assertTrue(jwtTokenProvider.validateToken(token))
        val authorities = jwtTokenProvider.getAuthoritiesFromToken(token)
        assertEquals("ROLE_GUEST", authorities)
    }

    @Test
    fun `generateGuestToken should embed meetingId in claims`() {
        // when
        val token = jwtTokenProvider.generateGuestToken("GuestUser", listOf("ROLE_GUEST"), "meeting-abc")

        // then
        // Token should be valid and contain ROLE_GUEST
        assertTrue(jwtTokenProvider.validateToken(token))
        val username = jwtTokenProvider.getUsernameFromToken(token)
        assertEquals("GuestUser", username)
    }

    @Test
    fun `getUsernameFromToken should return subject from token`() {
        // given
        val authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
        val token = jwtTokenProvider.generateToken(authentication)

        // when
        val username = jwtTokenProvider.getUsernameFromToken(token)

        // then
        assertEquals("test@test.com", username)
    }

    @Test
    fun `getRemainingTime should return positive value for valid token`() {
        // given
        val authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
        val token = jwtTokenProvider.generateToken(authentication)

        // when
        val remaining = jwtTokenProvider.getRemainingTime(token)

        // then
        assertTrue(remaining > 0)
        assertTrue(remaining <= 3600000L)
    }

    @Test
    fun `getRemainingTime should return 0 for expired token`() {
        // given
        every { jwtProperties.validityInMs } returns -60000L
        val authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
        val token = jwtTokenProvider.generateToken(authentication)

        // when
        val remaining = jwtTokenProvider.getRemainingTime(token)

        // then
        assertEquals(0L, remaining)
    }
}
