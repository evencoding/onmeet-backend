package com.onmeet.auth.service

import com.onmeet.auth.entity.Invitation
import com.onmeet.auth.entity.User
import com.onmeet.auth.repository.InvitationRepository
import com.onmeet.auth.repository.CompanyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional
class InvitationService(
    private val invitationRepository: InvitationRepository,
    private val companyRepository: CompanyRepository,
    private val emailService: EmailService,
    @org.springframework.beans.factory.annotation.Value("\${invitation.expiry-days}") private val invitationExpiryDays: Long
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
            expiresAt = LocalDateTime.now().plusDays(invitationExpiryDays) // Configurable expiry
        )
        
        emailService.sendInvitationEmail(email, code)
        
        return invitationRepository.save(invitation)
    }

    fun validateInvitation(email: String, code: String): Invitation {
        val invitation = invitationRepository.findByCode(code)
            .orElseThrow { IllegalArgumentException("Invalid invitation code") }

        if (invitation.email != email) {
            throw IllegalArgumentException("Email mismatch")
        }

        if (invitation.expiresAt.isBefore(LocalDateTime.now())) {
            throw IllegalArgumentException("Invitation expired")
        }

        return invitation
    }
    
    fun deleteInvitation(id: Long) {
        invitationRepository.deleteById(id)
    }
}
