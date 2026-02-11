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
    private lateinit var companyService: CompanyService

    @MockK
    private lateinit var teamService: TeamService

    @MockK
    private lateinit var invitationService: InvitationService
    
    @MockK
    private lateinit var jobTitleService: JobTitleService

    @MockK
    private lateinit var tokenService: TokenService
    
    // ...

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
        val jobTitle = JobTitle(name = "CEO", company = company)

        every { userRepository.existsByEmail(any()) } returns false
        every { companyService.createCompany(any()) } returns company
        every { jobTitleService.createDefaultInitialTitle(any()) } returns jobTitle
        every { teamService.createTeam(any<Long>(), any()) } returns team
        every { passwordEncoder.encode(any()) } returns "hashed_password"
        every { userRepository.save(any()) } returns User(
            id = 1L, 
            email = "test@example.com", 
            passwordHash = "hashed_password", 
            name = "Manager", 
            roles = mutableSetOf(User.Role.MANAGER), 
            company = company,
            status = User.UserStatus.ACTIVE
        )
        every { fileClient.generateDefaultProfileImage(any()) } returns null

        // when
        val userId = authService.signupCompany(request)

        // then
        assertEquals(1L, userId)
        verify { userRepository.save(any()) }
    }


    @MockK
    private lateinit var fileClient: com.onmeet.auth.client.FileClient

    @InjectMockKs
    private lateinit var authService: AuthService

    // ... (existing tests) ...

    @Test
    fun `resetUserProfileImage should delete old image and generate new default when manager requests`() {
        // given
        val company = Company(id = 1L, name = "TestCompany")
        val manager = User(
            id = 1L, 
            email = "manager@example.com", 
            passwordHash = "pw", 
            name = "Manager", 
            roles = mutableSetOf(User.Role.MANAGER), 
            company = company,
            status = User.UserStatus.ACTIVE
        )
        val employee = User(
            id = 2L, 
            email = "employee@example.com", 
            passwordHash = "pw", 
            name = "Employee", 
            roles = mutableSetOf(User.Role.USER), 
            company = company,
            status = User.UserStatus.ACTIVE
        ).apply { profileImageId = 100L }

        every { userRepository.findById(2L) } returns java.util.Optional.of(employee)
        every { userRepository.findByEmail("manager@example.com") } returns java.util.Optional.of(manager)
        every { fileClient.deleteFile(100L) } returns Unit
        every { fileClient.generateDefaultProfileImage("Employee") } returns com.onmeet.auth.client.FileMetadataResponse(
            id = 200L, 
            fileName = "new.svg", 
            s3Url = "s3://new.svg", 
            contentType = "image/svg+xml"
        )
        every { userRepository.save(any()) } returns employee

        // when
        authService.resetUserProfileImage(2L, "manager@example.com")

        // then
        verify { fileClient.deleteFile(100L) }
        verify { fileClient.generateDefaultProfileImage("Employee") }
        verify { userRepository.save(match { it.profileImageId == 200L }) }
    }

    @Test
    fun `resetUserProfileImage should throw UnauthorizedException when requester is not manager nor self`() {
        // given
        val company = Company(id = 1L, name = "TestCompany")
        val otherUser = User(id = 3L, email = "other@example.com", passwordHash = "pw", name = "Other", roles = mutableSetOf(User.Role.USER), company = company, status = User.UserStatus.ACTIVE)
        val employee = User(id = 2L, email = "employee@example.com", passwordHash = "pw", name = "Employee", roles = mutableSetOf(User.Role.USER), company = company, status = User.UserStatus.ACTIVE)

        every { userRepository.findById(2L) } returns java.util.Optional.of(employee)
        every { userRepository.findByEmail("other@example.com") } returns java.util.Optional.of(otherUser)

        // when & then
        assertThrows(UnauthorizedException::class.java) {
            authService.resetUserProfileImage(2L, "other@example.com")
        }
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
        val jobTitle = JobTitle(name = "Staff", company = company)

        every { invitationService.validateInvitation("join@example.com", "INVITE123") } returns invitation
        every { userRepository.existsByEmail("join@example.com") } returns false
        every { jobTitleService.getDefaultJobTitle(any()) } returns jobTitle
        every { passwordEncoder.encode(any()) } returns "hashed_password"
        every { userRepository.save(any()) } returns User(
            id = 2L,
            email = "join@example.com",
            passwordHash = "hashed_password",
            name = "Employee",
            roles = mutableSetOf(User.Role.USER),
            company = company,
            status = User.UserStatus.ACTIVE
        )
        every { invitationService.deleteInvitation(any()) } returns Unit
        every { fileClient.generateDefaultProfileImage(any()) } returns null

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
        val tokenResponse = TokenResponse("access_token", "refresh_token")
        
        every { authenticationManager.authenticate(any()) } returns authentication
        every { tokenService.issueTokens(authentication, "test@example.com") } returns tokenResponse

        // when
        val response = authService.login(request)

        // then
        assertEquals("access_token", response.accessToken)
        assertEquals("refresh_token", response.refreshToken)
        verify { tokenService.issueTokens(authentication, "test@example.com") }
    }
}
