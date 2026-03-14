package com.onmeet.auth.service

import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.Invitation
import com.onmeet.auth.entity.User
import com.onmeet.auth.repository.jpa.CompanyRepository
import com.onmeet.auth.repository.jpa.InvitationRepository
import com.onmeet.auth.config.InvitationProperties
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
import java.util.Optional
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
class InvitationServiceTest {

    @MockK
    lateinit var invitationRepository: InvitationRepository

    @MockK
    lateinit var companyRepository: CompanyRepository

    @MockK
    lateinit var emailService: EmailService

    @MockK
    lateinit var invitationProperties: InvitationProperties

    @InjectMockKs
    lateinit var invitationService: InvitationServiceImpl

    private val company = Company(id = 1L, name = "Test Company")

    @Test
    fun `createInvitation should create invitation and send email`() {
        // given
        val companyId = 1L
        val email = "invitee@example.com"
        val role = User.Role.USER
        val invitation = Invitation(
            id = 1L,
            company = company,
            email = email,
            role = role,
            code = "unique-code",
            expiresAt = LocalDateTime.now().plusDays(7),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        every { companyRepository.findById(companyId) } returns Optional.of(company)
        every { invitationProperties.expiryDays } returns 7L
        every { invitationRepository.findByEmail(email) } returns Optional.empty()
        every { invitationRepository.save(any()) } returns invitation
        every { emailService.sendInvitationEmail(any(), any(), any()) } returns Unit

        // when
        val result = invitationService.createInvitation(companyId, email, role)

        // then
        assertEquals(invitation, result)
        verify { companyRepository.findById(companyId) }
        verify { invitationRepository.save(any()) }
        verify { emailService.sendInvitationEmail(email, any(), "Test Company") }
    }

    @Test
    fun `createInvitation should throw BusinessException when active invitation already exists`() {
        // given
        val email = "invitee@example.com"
        val existingInvitation = Invitation(
            id = 1L,
            company = company,
            email = email,
            role = User.Role.USER,
            code = "existing-code",
            expiresAt = LocalDateTime.now().plusDays(3) // still active
        )

        every { companyRepository.findById(1L) } returns Optional.of(company)
        every { invitationProperties.expiryDays } returns 7L
        every { invitationRepository.findByEmail(email) } returns Optional.of(existingInvitation)

        // when & then
        assertThrows<BusinessException> {
            invitationService.createInvitation(1L, email, User.Role.USER)
        }
        verify(exactly = 0) { invitationRepository.save(any()) }
    }

    @Test
    fun `createInvitation should delete expired invitation and create new one`() {
        // given
        val email = "invitee@example.com"
        val expiredInvitation = Invitation(
            id = 1L,
            company = company,
            email = email,
            role = User.Role.USER,
            code = "expired-code",
            expiresAt = LocalDateTime.now().minusDays(1) // expired
        )
        val newInvitation = Invitation(
            id = 2L,
            company = company,
            email = email,
            role = User.Role.USER,
            code = "new-code",
            expiresAt = LocalDateTime.now().plusDays(7)
        )

        every { companyRepository.findById(1L) } returns Optional.of(company)
        every { invitationProperties.expiryDays } returns 7L
        every { invitationRepository.findByEmail(email) } returns Optional.of(expiredInvitation)
        every { invitationRepository.delete(expiredInvitation) } returns Unit
        every { invitationRepository.save(any()) } returns newInvitation
        every { emailService.sendInvitationEmail(any(), any(), any()) } returns Unit

        // when
        val result = invitationService.createInvitation(1L, email, User.Role.USER)

        // then
        verify { invitationRepository.delete(expiredInvitation) }
        verify { invitationRepository.save(any()) }
        assertEquals(newInvitation, result)
    }

    @Test
    fun `createInvitation should throw BusinessException when company not found`() {
        // given
        every { companyRepository.findById(999L) } returns Optional.empty()

        // when & then
        assertThrows<BusinessException> {
            invitationService.createInvitation(999L, "any@test.com", User.Role.USER)
        }
    }

    @Test
    fun `validateInvitation should return invitation when code and email are valid`() {
        // given
        val invitation = Invitation(
            id = 1L,
            company = company,
            email = "user@test.com",
            role = User.Role.USER,
            code = "valid-code",
            expiresAt = LocalDateTime.now().plusDays(1)
        )
        every { invitationRepository.findByCode("valid-code") } returns Optional.of(invitation)

        // when
        val result = invitationService.validateInvitation("user@test.com", "valid-code")

        // then
        assertEquals(invitation, result)
    }

    @Test
    fun `validateInvitation should throw BusinessException when code not found`() {
        // given
        every { invitationRepository.findByCode("nonexistent-code") } returns Optional.empty()

        // when & then
        assertThrows<BusinessException> {
            invitationService.validateInvitation("user@test.com", "nonexistent-code")
        }
    }

    @Test
    fun `validateInvitation should throw BusinessException when email does not match`() {
        // given
        val invitation = Invitation(
            id = 1L,
            company = company,
            email = "correct@test.com",
            role = User.Role.USER,
            code = "valid-code",
            expiresAt = LocalDateTime.now().plusDays(1)
        )
        every { invitationRepository.findByCode("valid-code") } returns Optional.of(invitation)

        // when & then
        assertThrows<BusinessException> {
            invitationService.validateInvitation("wrong@test.com", "valid-code")
        }
    }

    @Test
    fun `validateInvitation should throw BusinessException when invitation is expired`() {
        // given
        val invitation = Invitation(
            id = 1L,
            company = company,
            email = "user@test.com",
            role = User.Role.USER,
            code = "expired-code",
            expiresAt = LocalDateTime.now().minusDays(1) // expired
        )
        every { invitationRepository.findByCode("expired-code") } returns Optional.of(invitation)

        // when & then
        assertThrows<BusinessException> {
            invitationService.validateInvitation("user@test.com", "expired-code")
        }
    }

    @Test
    fun `deleteInvitation should delete when invitation exists`() {
        // given
        every { invitationRepository.existsById(1L) } returns true
        every { invitationRepository.deleteById(1L) } returns Unit

        // when
        invitationService.deleteInvitation(1L)

        // then
        verify { invitationRepository.deleteById(1L) }
    }

    @Test
    fun `deleteInvitation should throw BusinessException when invitation not found`() {
        // given
        every { invitationRepository.existsById(999L) } returns false

        // when & then
        assertThrows<BusinessException> {
            invitationService.deleteInvitation(999L)
        }
        verify(exactly = 0) { invitationRepository.deleteById(any()) }
    }
}
