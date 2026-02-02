package com.onmeet.auth.service

import com.onmeet.auth.entity.Invitation
import com.onmeet.auth.entity.User
import com.onmeet.auth.repository.jpa.InvitationRepository
import com.onmeet.auth.repository.jpa.CompanyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID
import com.onmeet.auth.config.InvitationProperties

@Service
@Transactional
class InvitationService(
    private val invitationRepository: InvitationRepository,
    private val companyRepository: CompanyRepository,
    private val emailService: EmailService,
    private val invitationProperties: InvitationProperties
) {

    fun createInvitation(companyId: Long, email: String, role: User.Role): Invitation {
        val company = companyRepository.findById(companyId)
            .orElseThrow { IllegalArgumentException("Company not found") }
            
        // Check for existing pending invitation
        invitationRepository.findByEmail(email).ifPresent {
             if (it.expiresAt.isAfter(LocalDateTime.now())) {
                 throw IllegalArgumentException("Active invitation already exists")
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
            expiresAt = LocalDateTime.now().plusDays(invitationProperties.expiryDays) // Configurable expiry
        )
        
        emailService.sendInvitationEmail(email, code)
        
        return invitationRepository.save(invitation)
    }

    fun validateInvitation(email: String, code: String): Invitation {
        val invitation = invitationRepository.findByCode(code)
            .orElseThrow { IllegalArgumentException("Invalid invitation code or email") }

        if (invitation.email != email || invitation.expiresAt.isBefore(LocalDateTime.now())) {
            throw IllegalArgumentException("Invalid invitation code or email")
        }

        return invitation
    }
    
    fun deleteInvitation(id: Long) {
        invitationRepository.deleteById(id)
    }

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 0 * * *")
    fun deleteExpiredInvitations() {
        invitationRepository.deleteByExpiresAtBefore(LocalDateTime.now())
    }
}
