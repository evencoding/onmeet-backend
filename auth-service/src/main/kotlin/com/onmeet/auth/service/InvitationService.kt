package com.onmeet.auth.service

import com.onmeet.auth.entity.Invitation
import com.onmeet.auth.entity.User
import com.onmeet.auth.entity.Company

interface InvitationService {
    fun createInvitation(companyId: Long, email: String, role: User.Role = User.Role.USER): Invitation
    fun createInvitation(company: Company, email: String, role: User.Role = User.Role.USER): Invitation
    fun validateInvitation(email: String, code: String): Invitation
    fun deleteInvitation(id: Long)
    fun deleteExpiredInvitations()
}
