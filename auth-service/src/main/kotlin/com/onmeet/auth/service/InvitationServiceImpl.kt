package com.onmeet.auth.service

import com.onmeet.auth.entity.*
import com.onmeet.auth.exception.*
import com.onmeet.auth.repository.jpa.InvitationRepository
import com.onmeet.common.exception.BusinessException
import com.onmeet.common.exception.errorcode.AuthErrorCode
import com.onmeet.auth.repository.jpa.CompanyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID
import com.onmeet.auth.config.InvitationProperties

@Service
@Transactional
class InvitationServiceImpl(
    private val invitationRepository: InvitationRepository,
    private val companyRepository: CompanyRepository,
    private val emailService: EmailService,
    private val invitationProperties: InvitationProperties
) : InvitationService {

    override fun createInvitation(companyId: Long, email: String, role: User.Role): Invitation {
        val company = companyRepository.findById(companyId)
            .orElseThrow { BusinessException(AuthErrorCode.COMPANY_NOT_FOUND) }
        return createInvitation(company, email, role)
    }

    override fun createInvitation(company: Company, email: String, role: User.Role): Invitation {
        // Check for existing pending invitation
        invitationRepository.findByEmail(email).ifPresent {
             if (it.expiresAt.isAfter(LocalDateTime.now())) {
                 throw BusinessException(AuthErrorCode.ACTIVE_INVITATION_EXISTS)
             } else {
                 invitationRepository.delete(it)
             }
        }

        val code = UUID.randomUUID().toString()
        val invitation = Invitation(
            email = email,
            code = code,
            role = role,
            company = company,
            expiresAt = LocalDateTime.now().plusDays(invitationProperties.expiryDays)
        )
        
        val saved = invitationRepository.save(invitation)
        emailService.sendInvitationEmail(email, code, company.name)

        return saved
    }

    override fun validateInvitation(email: String, code: String): Invitation {
        val invitation = invitationRepository.findByCode(code)
            .orElseThrow { BusinessException(AuthErrorCode.INVITATION_NOT_FOUND) }

        if (invitation.email != email || invitation.expiresAt.isBefore(LocalDateTime.now())) {
            throw BusinessException(AuthErrorCode.INVALID_INVITATION)
        }

        return invitation
    }
    
    override fun deleteInvitation(id: Long) {
        if (!invitationRepository.existsById(id)) {
            throw BusinessException(AuthErrorCode.INVITATION_NOT_FOUND)
        }
        invitationRepository.deleteById(id)
    }

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 0 * * *")
    override fun deleteExpiredInvitations() =
        invitationRepository.deleteByExpiresAtBefore(LocalDateTime.now())
}
