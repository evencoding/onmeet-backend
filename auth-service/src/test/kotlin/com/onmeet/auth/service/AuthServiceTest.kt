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
    
    @MockK
    private lateinit var withdrawnUserRepository: com.onmeet.auth.repository.jpa.WithdrawnUserRepository
    
    // ...

    @Test
    // [Essential] 비밀번호 변경 로직 및 해싱 검증
    fun `changePassword should update password if old password matches`() {
        // given
        val user = User(
            id = 1L, email = "test@example.com", passwordHash = "hashed_old", name = "Name", 
            roles = mutableSetOf(User.Role.USER), company = Company(1L, "Test"), status = User.UserStatus.ACTIVE
        )
        val request = ChangePasswordRequest(oldPassword = "old", newPassword = "new")

        every { userRepository.findByEmail("test@example.com") } returns java.util.Optional.of(user)
        every { passwordEncoder.matches("old", "hashed_old") } returns true
        every { passwordEncoder.encode("new") } returns "hashed_new"
        every { userRepository.save(any()) } returns user

        // when
        authService.changePassword("test@example.com", request)

        // then
        assertEquals("hashed_new", user.passwordHash)
        verify { userRepository.save(user) }
    }

    @Test
    fun `changePassword should throw exception if old password does not match`() {
        // given
        val user = User(
            id = 1L, email = "test@example.com", passwordHash = "hashed_old", name = "Name", 
            roles = mutableSetOf(User.Role.USER), company = Company(1L, "Test"), status = User.UserStatus.ACTIVE
        )
        val request = ChangePasswordRequest(oldPassword = "wrong", newPassword = "new")

        every { userRepository.findByEmail("test@example.com") } returns java.util.Optional.of(user)
        every { passwordEncoder.matches("wrong", "hashed_old") } returns false

        // when & then
        assertThrows(InvalidPasswordException::class.java) {
            authService.changePassword("test@example.com", request)
        }
    }


    @Test
    // [Essential] 기업 회원가입 및 초기 설정(팀/직무) 로직 검증
    fun `signupCompany should save manager and return id`() {
        // given
        val request = CompanySignupRequest(
            email = "test@example.com",
            password = "password",
            name = "Manager",
            companyName = "TestCompany"
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
    // [Essential] 프로필 이미지 초기화 권한 및 연동 로직 검증 (관리자 권한 포함)
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
    fun `resetUserProfileImage should throw UnauthorizedException when requester is self but not a manager`() {
        // given
        val company = Company(id = 1L, name = "TestCompany")
        val employee = User(
            id = 2L, 
            email = "employee@example.com", 
            passwordHash = "pw", 
            name = "Employee", 
            roles = mutableSetOf(User.Role.USER), 
            company = company,
            status = User.UserStatus.ACTIVE
        )

        every { userRepository.findById(2L) } returns java.util.Optional.of(employee)
        every { userRepository.findByEmail("employee@example.com") } returns java.util.Optional.of(employee)

        // when & then
        assertThrows(UnauthorizedException::class.java) {
            authService.resetUserProfileImage(2L, "employee@example.com")
        }
    }

    @Test
    fun `resetUserProfileImage should throw UnauthorizedException when requester is not a manager`() {
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
            companyName = "TestCompany"
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

    @Test
    // [Bug-1, Bug-2 FIX] 회원 탈퇴 시 프로필 이미지 삭제 및 토큰 무효화 검증
    fun `withdraw should delete profile image, archive user, anonymize data, and revoke tokens`() {
        // given
        val company = Company(id = 1L, name = "TestCompany")
        val user = User(
            id = 1L,
            email = "user@example.com",
            passwordHash = "hashed_password",
            name = "User Name",
            roles = mutableSetOf(User.Role.USER),
            company = company,
            status = User.UserStatus.ACTIVE
        ).apply { profileImageId = 100L }
        val request = WithdrawRequest(password = "password", reason = "Just leaving")

        every { userRepository.findByEmail("user@example.com") } returns java.util.Optional.of(user)
        every { passwordEncoder.matches("password", "hashed_password") } returns true
        every { withdrawnUserRepository.save(any()) } returns io.mockk.mockk()
        every { fileClient.deleteMyProfileImage() } returns Unit
        every { userRepository.save(any()) } returns user
        every { tokenService.revokeTokens(null, "user@example.com") } returns Unit

        // when
        authService.withdraw("user@example.com", request)

        // then
        verify { fileClient.deleteMyProfileImage() }
        verify { withdrawnUserRepository.save(match {
            it.originalUserId == 1L && it.email == "user@example.com" && it.reason == "Just leaving"
        }) }
        verify { userRepository.save(match {
            it.email == "withdrawn_1@onmeet.deleted" &&
            it.name == "Withdrawn User" &&
            it.passwordHash == "" &&
            it.status == User.UserStatus.INACTIVE &&
            it.profileImageId == null
        }) }
        verify { tokenService.revokeTokens(null, "user@example.com") }
    }

    @Test
    fun `withdraw should succeed even if profile image deletion fails`() {
        // given
        val company = Company(id = 1L, name = "TestCompany")
        val user = User(
            id = 1L,
            email = "user@example.com",
            passwordHash = "hashed_password",
            name = "User Name",
            roles = mutableSetOf(User.Role.USER),
            company = company,
            status = User.UserStatus.ACTIVE
        ).apply { profileImageId = 100L }
        val request = WithdrawRequest(password = "password", reason = "Leaving")

        every { userRepository.findByEmail("user@example.com") } returns java.util.Optional.of(user)
        every { passwordEncoder.matches("password", "hashed_password") } returns true
        every { withdrawnUserRepository.save(any()) } returns io.mockk.mockk()
        every { fileClient.deleteMyProfileImage() } throws RuntimeException("S3 error")
        every { userRepository.save(any()) } returns user
        every { tokenService.revokeTokens(null, "user@example.com") } returns Unit

        // when
        authService.withdraw("user@example.com", request)

        // then - should complete successfully despite file deletion failure
        verify { withdrawnUserRepository.save(any()) }
        verify { userRepository.save(any()) }
        verify { tokenService.revokeTokens(null, "user@example.com") }
    }

    @Test
    fun `withdraw should not attempt to delete profile image when user has none`() {
        // given
        val company = Company(id = 1L, name = "TestCompany")
        val user = User(
            id = 1L,
            email = "user@example.com",
            passwordHash = "hashed_password",
            name = "User Name",
            roles = mutableSetOf(User.Role.USER),
            company = company,
            status = User.UserStatus.ACTIVE
        ) // profileImageId is null
        val request = WithdrawRequest(password = "password", reason = "Leaving")

        every { userRepository.findByEmail("user@example.com") } returns java.util.Optional.of(user)
        every { passwordEncoder.matches("password", "hashed_password") } returns true
        every { withdrawnUserRepository.save(any()) } returns io.mockk.mockk()
        every { userRepository.save(any()) } returns user
        every { tokenService.revokeTokens(null, "user@example.com") } returns Unit

        // when
        authService.withdraw("user@example.com", request)

        // then
        verify(exactly = 0) { fileClient.deleteMyProfileImage() }
        verify { withdrawnUserRepository.save(any()) }
        verify { userRepository.save(any()) }
        verify { tokenService.revokeTokens(null, "user@example.com") }
    }

    @Test
    fun `withdraw should throw InvalidPasswordException when password does not match`() {
        // given
        val company = Company(id = 1L, name = "TestCompany")
        val user = User(
            id = 1L,
            email = "user@example.com",
            passwordHash = "hashed_password",
            name = "User Name",
            roles = mutableSetOf(User.Role.USER),
            company = company,
            status = User.UserStatus.ACTIVE
        )
        val request = WithdrawRequest(password = "wrong_password", reason = "Leaving")

        every { userRepository.findByEmail("user@example.com") } returns java.util.Optional.of(user)
        every { passwordEncoder.matches("wrong_password", "hashed_password") } returns false

        // when & then
        assertThrows(InvalidPasswordException::class.java) {
            authService.withdraw("user@example.com", request)
        }
        verify(exactly = 0) { withdrawnUserRepository.save(any()) }
        verify(exactly = 0) { tokenService.revokeTokens(any(), any()) }
    }

    @Test
    // [Bug-8 Edge Case] 기존 프로필 이미지가 있는 사용자가 회원가입 시 기존 이미지가 삭제되어야 함
    fun `signupCompany should delete old profile image when user already has profileImageId`() {
        // given
        val request = CompanySignupRequest(
            email = "test@example.com",
            password = "password",
            name = "Manager",
            companyName = "TestCompany"
        )
        val company = Company(id = 1L, name = "TestCompany")
        val team = Team(id = 1L, name = "Development", description = "Initial team", color = "#FFFFFF", company = company)
        val jobTitle = JobTitle(name = "CEO", company = company)

        // 기존 프로필 이미지 ID를 가진 사용자 시뮬레이션
        val userWithOldProfile = User(
            id = 1L,
            email = "test@example.com",
            passwordHash = "hashed_password",
            name = "Manager",
            roles = mutableSetOf(User.Role.MANAGER),
            company = company,
            status = User.UserStatus.ACTIVE
        ).apply { profileImageId = 999L } // 기존 프로필 이미지 ID 설정

        every { userRepository.existsByEmail(any()) } returns false
        every { companyService.createCompany(any()) } returns company
        every { jobTitleService.createDefaultInitialTitle(any()) } returns jobTitle
        every { teamService.createTeam(any<Long>(), any()) } returns team
        every { passwordEncoder.encode(any()) } returns "hashed_password"
        every { userRepository.save(any()) } returns userWithOldProfile
        every { fileClient.deleteMyProfileImage() } returns Unit
        every { fileClient.generateDefaultProfileImage(any()) } returns com.onmeet.auth.client.FileMetadataResponse(
            id = 200L,
            fileName = "new.svg",
            s3Url = "s3://new.svg",
            contentType = "image/svg+xml"
        )

        // when
        val userId = authService.signupCompany(request)

        // then
        assertEquals(1L, userId)
        // 기존 프로필 이미지가 있으므로 정확히 1회 삭제 호출되어야 함
        verify(exactly = 1) { fileClient.deleteMyProfileImage() }
        verify { fileClient.generateDefaultProfileImage("Manager") }
        verify { userRepository.save(any()) }
    }

    @Test
    // [Bug-8 Edge Case] 프로필 이미지가 없는 사용자는 삭제 호출이 발생하지 않아야 함
    fun `signupCompany should not delete profile image when user has no profileImageId`() {
        // given
        val request = CompanySignupRequest(
            email = "test@example.com",
            password = "password",
            name = "Manager",
            companyName = "TestCompany"
        )
        val company = Company(id = 1L, name = "TestCompany")
        val team = Team(id = 1L, name = "Development", description = "Initial team", color = "#FFFFFF", company = company)
        val jobTitle = JobTitle(name = "CEO", company = company)

        // profileImageId가 null인 사용자
        val userWithoutProfile = User(
            id = 1L,
            email = "test@example.com",
            passwordHash = "hashed_password",
            name = "Manager",
            roles = mutableSetOf(User.Role.MANAGER),
            company = company,
            status = User.UserStatus.ACTIVE
        ) // profileImageId is null

        every { userRepository.existsByEmail(any()) } returns false
        every { companyService.createCompany(any()) } returns company
        every { jobTitleService.createDefaultInitialTitle(any()) } returns jobTitle
        every { teamService.createTeam(any<Long>(), any()) } returns team
        every { passwordEncoder.encode(any()) } returns "hashed_password"
        every { userRepository.save(any()) } returns userWithoutProfile
        every { fileClient.generateDefaultProfileImage(any()) } returns com.onmeet.auth.client.FileMetadataResponse(
            id = 200L,
            fileName = "new.svg",
            s3Url = "s3://new.svg",
            contentType = "image/svg+xml"
        )

        // when
        val userId = authService.signupCompany(request)

        // then
        assertEquals(1L, userId)
        // 기존 프로필 이미지가 없으므로 삭제 호출이 0회여야 함
        verify(exactly = 0) { fileClient.deleteMyProfileImage() }
        verify { fileClient.generateDefaultProfileImage("Manager") }
        verify { userRepository.save(any()) }
    }
}
