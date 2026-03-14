package com.onmeet.auth.service

import com.onmeet.auth.client.FileClient
import com.onmeet.auth.dto.CompanySignupRequest
import com.onmeet.auth.dto.JoinRequest
import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.Invitation
import com.onmeet.auth.entity.JobTitle
import com.onmeet.auth.entity.User
import com.onmeet.auth.repository.jpa.UserRepository
import com.onmeet.common.exception.BusinessException
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
class SignupServiceTest {

    @MockK
    private lateinit var companyService: CompanyService

    @MockK
    private lateinit var invitationService: InvitationService

    @MockK
    private lateinit var jobTitleService: JobTitleService

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var passwordEncoder: PasswordEncoder

    @MockK
    private lateinit var fileClient: FileClient

    @InjectMockKs
    private lateinit var signupService: SignupService

    private val company = Company(id = 1L, name = "Test Corp")
    private val jobTitle = JobTitle(id = 1L, name = "선택 안함", company = company, isDefault = true)

    @Test
    fun `signupCompany should create manager user and return user id`() {
        // given
        val request = CompanySignupRequest(
            email = "manager@test.com",
            password = "password",
            name = "Manager",
            companyName = "Test Corp"
        )
        val savedUser = User(
            id = 1L,
            email = request.email,
            passwordHash = "hashed",
            name = request.name,
            roles = mutableSetOf(User.Role.MANAGER),
            company = company,
            status = User.UserStatus.ACTIVE
        )
        every { userRepository.existsByEmail(request.email) } returns false
        every { companyService.createCompany("Test Corp") } returns company
        every { jobTitleService.createDefaultInitialTitle(company) } returns jobTitle
        every { passwordEncoder.encode(request.password) } returns "hashed"
        every { userRepository.save(any()) } returns savedUser
        every { fileClient.generateDefaultProfileImage(request.name) } returns null

        // when
        val result = signupService.signupCompany(request, null)

        // then
        assertEquals(1L, result)
        verify { companyService.createCompany("Test Corp") }
        verify { jobTitleService.createDefaultInitialTitle(company) }
        verify { userRepository.save(any()) }
    }

    @Test
    fun `signupCompany should throw BusinessException when email already exists`() {
        // given
        val request = CompanySignupRequest(
            email = "duplicate@test.com",
            password = "password",
            name = "Manager",
            companyName = "Test Corp"
        )
        every { userRepository.existsByEmail(request.email) } returns true

        // when & then
        assertThrows<BusinessException> {
            signupService.signupCompany(request, null)
        }
        verify(exactly = 0) { companyService.createCompany(any()) }
    }

    @Test
    fun `joinCompany should save user and delete invitation after joining`() {
        // given
        val request = JoinRequest(
            email = "employee@test.com",
            password = "password",
            name = "Employee",
            employeeId = "EMP001",
            code = "INVITE_CODE"
        )
        val invitation = Invitation(
            id = 1L,
            email = request.email,
            code = request.code,
            role = User.Role.USER,
            company = company,
            expiresAt = LocalDateTime.now().plusDays(1)
        )
        val savedUser = User(
            id = 2L,
            email = request.email,
            passwordHash = "hashed",
            name = request.name,
            roles = mutableSetOf(User.Role.USER),
            company = company,
            status = User.UserStatus.ACTIVE
        )
        every { invitationService.validateInvitation(request.email, request.code) } returns invitation
        every { userRepository.existsByEmail(request.email) } returns false
        every { jobTitleService.getDefaultJobTitle(company) } returns jobTitle
        every { passwordEncoder.encode(request.password) } returns "hashed"
        every { userRepository.save(any()) } returns savedUser
        every { invitationService.deleteInvitation(1L) } returns Unit
        every { fileClient.generateDefaultProfileImage(request.name) } returns null

        // when
        val result = signupService.joinCompany(request, null)

        // then
        assertEquals(2L, result)
        verify { invitationService.validateInvitation(request.email, request.code) }
        verify { invitationService.deleteInvitation(1L) }
        verify { userRepository.save(any()) }
    }

    @Test
    fun `joinCompany should throw BusinessException when email already registered`() {
        // given
        val request = JoinRequest(
            email = "existing@test.com",
            password = "password",
            name = "Employee",
            code = "INVITE_CODE"
        )
        val invitation = Invitation(
            id = 1L,
            email = request.email,
            code = request.code,
            role = User.Role.USER,
            company = company,
            expiresAt = LocalDateTime.now().plusDays(1)
        )
        every { invitationService.validateInvitation(request.email, request.code) } returns invitation
        every { userRepository.existsByEmail(request.email) } returns true

        // when & then
        assertThrows<BusinessException> {
            signupService.joinCompany(request, null)
        }
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `joinCompany should assign role from invitation`() {
        // given
        val request = JoinRequest(
            email = "manager@test.com",
            password = "password",
            name = "Manager",
            code = "INVITE_CODE"
        )
        val invitation = Invitation(
            id = 1L,
            email = request.email,
            code = request.code,
            role = User.Role.MANAGER,  // invited as manager
            company = company,
            expiresAt = LocalDateTime.now().plusDays(1)
        )
        val savedUser = User(
            id = 2L,
            email = request.email,
            passwordHash = "hashed",
            name = request.name,
            roles = mutableSetOf(User.Role.MANAGER),
            company = company,
            status = User.UserStatus.ACTIVE
        )
        every { invitationService.validateInvitation(request.email, request.code) } returns invitation
        every { userRepository.existsByEmail(request.email) } returns false
        every { jobTitleService.getDefaultJobTitle(company) } returns jobTitle
        every { passwordEncoder.encode(any()) } returns "hashed"
        every { userRepository.save(any()) } returns savedUser
        every { invitationService.deleteInvitation(1L) } returns Unit
        every { fileClient.generateDefaultProfileImage(any()) } returns null

        // when
        signupService.joinCompany(request, null)

        // then - verify user is saved with MANAGER role (from invitation)
        verify {
            userRepository.save(match { it.roles.contains(User.Role.MANAGER) })
        }
    }
}
