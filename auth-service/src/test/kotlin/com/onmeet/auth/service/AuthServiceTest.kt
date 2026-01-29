package com.onmeet.auth.service

import com.onmeet.auth.dto.CompanySignupRequest
import com.onmeet.auth.dto.LoginRequest
import com.onmeet.auth.dto.TokenResponse
import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.RefreshToken
import com.onmeet.auth.entity.Team
import com.onmeet.auth.entity.User
import com.onmeet.auth.repository.RefreshTokenRepository
import com.onmeet.auth.repository.UserRepository
import com.onmeet.auth.security.JwtTokenProvider
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.*
import java.util.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.password.PasswordEncoder

// Helper function to handle Kotlin non-null constraints with Mockito any()
private fun <T> any(type: Class<T>): T? = Mockito.any(type)

@ExtendWith(MockitoExtension::class)
class AuthServiceTest {
    // ... dependencies

    @Mock
    lateinit var userRepository: UserRepository

    @Mock
    lateinit var passwordEncoder: PasswordEncoder

    @Mock
    lateinit var authenticationManager: AuthenticationManager

    @Mock
    lateinit var jwtTokenProvider: JwtTokenProvider

    @Mock
    lateinit var companyService: CompanyService

    @Mock
    lateinit var invitationService: InvitationService

    @Mock
    lateinit var refreshTokenRepository: RefreshTokenRepository

    @InjectMocks
    lateinit var authService: AuthService

    @Test
    fun `signupCompany should create company, team and manager`() {
        // Given
        val request = CompanySignupRequest(
            email = "manager@test.com",
            password = "password",
            name = "Manager",
            companyName = "Test Corp",
            teamName = "Dev Team"
        )
        val company = Company(id = 1L, name = request.companyName)
        val team = Team(id = 1L, name = request.teamName, company = company)
        val savedUser = User(
            id = 1L,
            email = request.email,
            passwordHash = "encodedRequestPassword",
            name = request.name,
            role = User.Role.MANAGER,
            company = company,
            team = team
        )
        val dummyTeamRequest = com.onmeet.auth.dto.TeamRequest("", null, null)

        `when`(userRepository.existsByEmail(request.email)).thenReturn(false)
        `when`(companyService.createCompany(request.companyName)).thenReturn(company)
        
        `when`(companyService.createTeam(eq(1L), any(com.onmeet.auth.dto.TeamRequest::class.java) ?: dummyTeamRequest)).thenReturn(team)
        
        `when`(passwordEncoder.encode(request.password)).thenReturn("encodedRequestPassword")
        `when`(userRepository.save(any(User::class.java) ?: savedUser)).thenReturn(savedUser)

        // When
        val userId = authService.signupCompany(request)

        // Then
        assertEquals(1L, userId)
        verify(companyService).createCompany(request.companyName)
        verify(companyService).createTeam(eq(1L), any(com.onmeet.auth.dto.TeamRequest::class.java) ?: dummyTeamRequest)
        verify(userRepository).save(any(User::class.java) ?: savedUser)
    }

    @Test
    fun `signupCompany should throw exception when email already exists`() {
        // Given
        val request = CompanySignupRequest(
            email = "existing@test.com",
            password = "password",
            name = "Manager",
            companyName = "Test Corp",
            teamName = "Dev Team"
        )
        
        `when`(userRepository.existsByEmail(request.email)).thenReturn(true)

        // When & Then
        assertThrows(IllegalArgumentException::class.java) {
            authService.signupCompany(request)
        }
        
        verify(companyService, never()).createCompany(anyString())
    }

    @Test
    fun `login should return access and refresh token`() {
        // Given
        val request = LoginRequest("test@test.com", "password")
        val authentication = mock(Authentication::class.java)
        val accessToken = "access-token"
        
        `when`(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken::class.java) ?: UsernamePasswordAuthenticationToken("","")))
            .thenReturn(authentication)
        `when`(jwtTokenProvider.generateToken(authentication)).thenReturn(accessToken)
        `when`(authentication.authorities).thenReturn(emptyList())

        // When
        val response = authService.login(request)

        // Then
        assertEquals(accessToken, response.accessToken)
        assertNotNull(response.refreshToken)
        
        verify(refreshTokenRepository).save(any(RefreshToken::class.java) ?: RefreshToken(mobileOrEmail="", token="", authority=""))
    }
    
    @Test
    fun `login should throw exception when authentication fails`() {
        // Given
        val request = LoginRequest("test@test.com", "wrong_password")
        val dummyAuthToken = UsernamePasswordAuthenticationToken("", "")
        
        `when`(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken::class.java) ?: dummyAuthToken))
            .thenThrow(org.springframework.security.authentication.BadCredentialsException("Bad credentials"))

        // When & Then
        assertThrows(org.springframework.security.authentication.BadCredentialsException::class.java) {
            authService.login(request)
        }
        
        verifyNoInteractions(jwtTokenProvider)
        verifyNoInteractions(refreshTokenRepository)
    }

    @Test
    fun `joinCompany should create user and delete invitation`() {
        // Given
        val request = com.onmeet.auth.dto.JoinRequest(
            email = "join@test.com",
            code = "invite-code",
            password = "password",
            name = "Joiner",
            employeeId = "EMP123"
        )
        val company = Company(id = 1L, name = "Test Corp")
        val invitation = com.onmeet.auth.entity.Invitation(
            id = 1L,
            email = request.email,
            code = request.code,
            company = company,
            role = User.Role.USER,
            expiresAt = java.time.LocalDateTime.now().plusHours(24)
        )
        val savedUser = User(
            id = 2L,
            email = request.email,
            passwordHash = "encodedRequestPassword",
            name = request.name,
            role = User.Role.USER,
            company = company,
            employeeId = request.employeeId
        )

        `when`(invitationService.validateInvitation(request.email, request.code)).thenReturn(invitation)
        `when`(userRepository.existsByEmail(request.email)).thenReturn(false)
        `when`(passwordEncoder.encode(request.password)).thenReturn("encodedRequestPassword")
        `when`(userRepository.save(any(User::class.java) ?: savedUser)).thenReturn(savedUser)

        // When
        val userId = authService.joinCompany(request)

        // Then
        assertEquals(2L, userId)
        verify(invitationService).deleteInvitation(1L)
        verify(userRepository).save(any(User::class.java) ?: savedUser)
    }

    @Test
    fun `guestLogin should return access token only`() {
        // Given
        val request = com.onmeet.auth.dto.GuestLoginRequest("Guest", "meeting-123")
        val accessToken = "guest-access-token"
        
        `when`(jwtTokenProvider.generateGuestToken(request.name, request.meetingId)).thenReturn(accessToken)

        // When
        val response = authService.guestLogin(request)

        // Then
        assertEquals(accessToken, response.accessToken)
        assertNull(response.refreshToken)
        
        verify(jwtTokenProvider).generateGuestToken(request.name, request.meetingId)
    }
}
