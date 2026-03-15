package com.onmeet.auth.service

import com.onmeet.auth.client.FileClient
import com.onmeet.auth.client.FileMetadataResponse
import com.onmeet.auth.dto.*
import com.onmeet.auth.entity.*
import com.onmeet.auth.repository.jpa.UserRepository
import com.onmeet.common.exception.BusinessException
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.Optional

@ExtendWith(MockKExtension::class)
class AuthServiceTest {

    @MockK
    private lateinit var signupService: SignupService

    @MockK
    private lateinit var authenticationService: AuthenticationService

    @MockK
    private lateinit var withdrawService: WithdrawService

    @MockK
    private lateinit var passwordService: PasswordService

    @MockK
    private lateinit var invitationService: InvitationService

    @MockK
    private lateinit var fileClient: FileClient

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var notificationEventPublisher: NotificationEventPublisher

    @InjectMockKs
    private lateinit var authService: AuthService

    private val company = Company(id = 1L, name = "TestCompany")

    // ===== Delegation tests =====

    @Test
    fun `signupCompany should delegate to signupService`() {
        // given
        val request = CompanySignupRequest(
            email = "test@example.com", password = "password", name = "Manager", companyName = "TestCompany"
        )
        every { signupService.signupCompany(request, null) } returns 1L

        // when
        val result = authService.signupCompany(request)

        // then
        assertEquals(1L, result)
        verify { signupService.signupCompany(request, null) }
    }

    @Test
    fun `signupCompany should throw BusinessException when email already exists`() {
        // given
        val request = CompanySignupRequest(
            email = "exists@example.com", password = "password", name = "Manager", companyName = "TestCompany"
        )
        every { signupService.signupCompany(request, null) } throws BusinessException(
            com.onmeet.common.exception.errorcode.AuthErrorCode.EMAIL_ALREADY_EXISTS
        )

        // when & then
        assertThrows<BusinessException> {
            authService.signupCompany(request)
        }
    }

    @Test
    fun `joinCompany should delegate to signupService`() {
        // given
        val request = JoinRequest(
            email = "join@example.com", password = "password", name = "Employee",
            employeeId = "EMP001", code = "INVITE123"
        )
        every { signupService.joinCompany(request, null) } returns 2L

        // when
        val result = authService.joinCompany(request)

        // then
        assertEquals(2L, result)
        verify { signupService.joinCompany(request, null) }
    }

    @Test
    fun `login should delegate to authenticationService`() {
        // given
        val request = LoginRequest("test@example.com", "password")
        val tokenResponse = TokenResponse("access_token", "refresh_token")
        every { authenticationService.login(request) } returns tokenResponse

        // when
        val response = authService.login(request)

        // then
        assertEquals("access_token", response.accessToken)
        verify { authenticationService.login(request) }
    }

    @Test
    fun `refresh should delegate to authenticationService`() {
        // given
        val tokenResponse = TokenResponse("new_access", "new_refresh")
        every { authenticationService.refresh("old_token") } returns tokenResponse

        // when
        val result = authService.refresh("old_token")

        // then
        assertEquals("new_access", result.accessToken)
        verify { authenticationService.refresh("old_token") }
    }

    @Test
    fun `logout should delegate to authenticationService`() {
        // given
        every { authenticationService.logout("access", "test@example.com") } returns Unit

        // when
        authService.logout("access", "test@example.com")

        // then
        verify { authenticationService.logout("access", "test@example.com") }
    }

    @Test
    fun `withdraw should delegate to withdrawService`() {
        // given
        val request = WithdrawRequest(password = "password", reason = "Leaving")
        every { withdrawService.withdraw("user@example.com", request) } returns Unit

        // when
        authService.withdraw("user@example.com", request)

        // then
        verify { withdrawService.withdraw("user@example.com", request) }
    }

    @Test
    fun `changePassword should delegate to passwordService`() {
        // given
        val request = ChangePasswordRequest(oldPassword = "old", newPassword = "new")
        every { passwordService.changePassword("test@example.com", request) } returns Unit

        // when
        authService.changePassword("test@example.com", request)

        // then
        verify { passwordService.changePassword("test@example.com", request) }
    }

    @Test
    fun `changePassword should propagate BusinessException from passwordService`() {
        // given
        val request = ChangePasswordRequest(oldPassword = "wrong", newPassword = "new")
        every { passwordService.changePassword("test@example.com", request) } throws BusinessException(
            com.onmeet.common.exception.errorcode.AuthErrorCode.CURRENT_PASSWORD_MISMATCH
        )

        // when & then
        assertThrows<BusinessException> {
            authService.changePassword("test@example.com", request)
        }
    }

    @Test
    fun `findPassword should delegate to passwordService`() {
        // given
        every { passwordService.findPassword("test@example.com") } returns Unit

        // when
        authService.findPassword("test@example.com")

        // then
        verify { passwordService.findPassword("test@example.com") }
    }

    // ===== resetUserProfileImage tests (lives in AuthService directly) =====

    @Test
    fun `resetUserProfileImage should delete old image and generate new default when manager requests`() {
        // given
        val manager = User(
            id = 1L, email = "manager@example.com", passwordHash = "pw", name = "Manager",
            roles = mutableSetOf(User.Role.MANAGER), company = company, status = User.UserStatus.ACTIVE
        )
        val employee = User(
            id = 2L, email = "employee@example.com", passwordHash = "pw", name = "Employee",
            roles = mutableSetOf(User.Role.USER), company = company, status = User.UserStatus.ACTIVE
        ).apply { profileImageId = 100L }

        every { userRepository.findById(2L) } returns Optional.of(employee)
        every { userRepository.findByEmail("manager@example.com") } returns Optional.of(manager)
        every { fileClient.deleteFile(100L) } returns Unit
        every { fileClient.generateDefaultProfileImage("Employee", any()) } returns FileMetadataResponse(
            id = 200L, fileName = "new.svg", s3Url = "s3://new.svg", contentType = "image/svg+xml"
        )
        every { userRepository.save(any()) } returns employee
        every { notificationEventPublisher.publishNotification(any()) } returns Unit

        // when
        authService.resetUserProfileImage(2L, "manager@example.com")

        // then
        verify { fileClient.deleteFile(100L) }
        verify { fileClient.generateDefaultProfileImage("Employee", any()) }
        verify { userRepository.save(match { it.profileImageId == 200L }) }
    }

    @Test
    fun `resetUserProfileImage should throw BusinessException when requester is not a manager`() {
        // given
        val employee = User(
            id = 2L, email = "employee@example.com", passwordHash = "pw", name = "Employee",
            roles = mutableSetOf(User.Role.USER), company = company, status = User.UserStatus.ACTIVE
        )
        val otherUser = User(
            id = 3L, email = "other@example.com", passwordHash = "pw", name = "Other",
            roles = mutableSetOf(User.Role.USER), company = company, status = User.UserStatus.ACTIVE
        )

        every { userRepository.findById(2L) } returns Optional.of(employee)
        every { userRepository.findByEmail("other@example.com") } returns Optional.of(otherUser)

        // when & then
        assertThrows<BusinessException> {
            authService.resetUserProfileImage(2L, "other@example.com")
        }
    }

    @Test
    fun `resetUserProfileImage should succeed even if old image deletion fails`() {
        // given
        val manager = User(
            id = 1L, email = "manager@example.com", passwordHash = "pw", name = "Manager",
            roles = mutableSetOf(User.Role.MANAGER), company = company, status = User.UserStatus.ACTIVE
        )
        val employee = User(
            id = 2L, email = "employee@example.com", passwordHash = "pw", name = "Employee",
            roles = mutableSetOf(User.Role.USER), company = company, status = User.UserStatus.ACTIVE
        ).apply { profileImageId = 100L }

        every { userRepository.findById(2L) } returns Optional.of(employee)
        every { userRepository.findByEmail("manager@example.com") } returns Optional.of(manager)
        every { fileClient.deleteFile(100L) } throws RuntimeException("S3 error")
        every { fileClient.generateDefaultProfileImage("Employee", any()) } returns FileMetadataResponse(
            id = 200L, fileName = "new.svg", s3Url = "s3://new.svg", contentType = "image/svg+xml"
        )
        every { userRepository.save(any()) } returns employee
        every { notificationEventPublisher.publishNotification(any()) } returns Unit

        // when - should not throw despite S3 failure
        authService.resetUserProfileImage(2L, "manager@example.com")

        // then
        verify { fileClient.generateDefaultProfileImage("Employee", any()) }
        verify { userRepository.save(match { it.profileImageId == 200L }) }
    }
}
