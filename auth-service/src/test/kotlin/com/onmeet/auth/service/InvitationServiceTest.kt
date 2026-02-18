package com.onmeet.auth.service

import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.Invitation
import com.onmeet.auth.entity.User
import com.onmeet.auth.repository.jpa.CompanyRepository
import com.onmeet.auth.repository.jpa.InvitationRepository
import com.onmeet.auth.config.InvitationProperties
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
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
    lateinit var invitationService: InvitationService

    @Test
    fun `createInvitation should create invitation and send email`() {
        // Given
        val companyId = 1L
        val email = "invitee@example.com"
        val role = User.Role.USER
        val company = Company(id = companyId, name = "Test Company")
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
        every { emailService.sendInvitationEmail(any(), any()) } returns Unit

        // When
        val result = invitationService.createInvitation(companyId, email, role)

        // Then
        assertEquals(invitation, result)
        verify { companyRepository.findById(companyId) }
        verify { invitationRepository.save(any()) }
        verify { emailService.sendInvitationEmail(email, any()) }
    }
}
