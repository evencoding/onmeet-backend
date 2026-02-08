package com.onmeet.auth.service

import com.onmeet.auth.dto.*
import com.onmeet.auth.entity.*
import com.onmeet.auth.config.TeamProperties
import com.onmeet.auth.exception.*
import com.onmeet.auth.repository.jpa.UserRepository
import com.onmeet.auth.repository.redis.RefreshTokenRepository
import com.onmeet.auth.security.JwtTokenProvider
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
class AuthServiceTest {

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var passwordEncoder: PasswordEncoder

    @MockK
    private lateinit var authenticationManager: AuthenticationManager

    @MockK
    private lateinit var jwtTokenProvider: JwtTokenProvider

    @MockK
    private lateinit var companyService: CompanyService

    @MockK
    private lateinit var invitationService: InvitationService

    @MockK
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @MockK
    private lateinit var teamProperties: TeamProperties

    @MockK
    private lateinit var redisTemplate: org.springframework.data.redis.core.StringRedisTemplate

    @InjectMockKs
    private lateinit var authService: AuthService

    @Test
    fun `signupCompany should save manager and return id`() {
        // given
        val request = CompanySignupRequest(
            email = "test@example.com",
            password = "password",
            name = "Manager",
            companyName = "TestCompany",
            teamName = "Development"
        )
        val company = Company(id = 1L, name = "TestCompany")
        val team = Team(id = 1L, name = "Development", description = "Initial team", color = "#FFFFFF", company = company)

        every { userRepository.existsByEmail(any()) } returns false
        every { companyService.createCompany(any()) } returns company
        every { teamProperties.initialDescription } returns "Initial team"
        every { teamProperties.initialColor } returns "#FFFFFF"
        every { companyService.createTeam(any(), any()) } returns team
        every { passwordEncoder.encode(any()) } returns "hashed_password"
        every { userRepository.save(any()) } returns User(id = 1L, email = "test@example.com", passwordHash = "hashed_password", name = "Manager", role = User.Role.MANAGER, company = company)

        // when
        val userId = authService.signupCompany(request)

        // then
        assertEquals(1L, userId)
        verify { userRepository.save(any()) }
    }

    @Test
    fun `signupCompany should throw exception if email exists`() {
        // given
        val request = CompanySignupRequest(
            email = "exists@example.com",
            password = "password",
            name = "Manager",
            companyName = "TestCompany",
            teamName = "Development"
        )
        every { userRepository.existsByEmail("exists@example.com") } returns true

        // when & then
        assertThrows(EmailAlreadyExistsException::class.java) {
            authService.signupCompany(request)
        }
    }

    @Test
    fun `joinCompany should save user and delete invitation`() {
        // given
        val request = JoinRequest(
            email = "join@example.com",
            password = "password",
            name = "Employee",
            employeeId = "EMP001",
            code = "INVITE123"
        )
        val company = Company(id = 1L, name = "TestCompany")
        val invitation = Invitation(
            id = 1L,
            email = "join@example.com",
            code = "INVITE123",
            role = User.Role.USER,
            company = company,
            expiresAt = LocalDateTime.now().plusDays(1)
        )

        every { invitationService.validateInvitation("join@example.com", "INVITE123") } returns invitation
        every { userRepository.existsByEmail("join@example.com") } returns false
        every { passwordEncoder.encode(any()) } returns "hashed_password"
        every { userRepository.save(any()) } returns User(
            id = 2L,
            email = "join@example.com",
            passwordHash = "hashed_password",
            name = "Employee",
            role = User.Role.USER,
            company = company
        )
        every { invitationService.deleteInvitation(any()) } returns Unit

        // when
        val userId = authService.joinCompany(request)

        // then
        assertEquals(2L, userId)
        verify { invitationService.deleteInvitation(1L) }
        verify { userRepository.save(any()) }
    }

    @Test
    fun `login should return tokens`() {
        // given
        val request = LoginRequest("test@example.com", "password")
        val authentication = io.mockk.mockk<org.springframework.security.core.Authentication>()
        
        every { authenticationManager.authenticate(any()) } returns authentication
        every { jwtTokenProvider.generateToken(authentication) } returns "access_token"
        every { authentication.authorities } returns mutableListOf()
        every { refreshTokenRepository.save(any()) } returns RefreshToken("test@example.com", "refresh_token", "")

        // when
        val response = authService.login(request)

        // then
        assertEquals("access_token", response.accessToken)
        assertNotNull(response.refreshToken)
        verify { refreshTokenRepository.save(any()) }
    }
}
