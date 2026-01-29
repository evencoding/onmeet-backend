package com.onmeet.auth.controller

import com.onmeet.auth.dto.InvitationRequest
import com.onmeet.auth.dto.TeamRequest
import com.onmeet.auth.entity.User
import com.onmeet.auth.service.CompanyService
import com.onmeet.auth.service.InvitationService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/companies")
class CompanyController(
    private val companyService: CompanyService,
    private val invitationService: InvitationService
) {

    @PostMapping("/teams")
    @PreAuthorize("hasRole('MANAGER')")
    fun createTeam(
        @AuthenticationPrincipal user: User,
        @RequestBody request: TeamRequest
    ): ResponseEntity<Long> {
        val companyId = user.company?.id ?: throw IllegalStateException("User does not belong to a company")
        val team = companyService.createTeam(companyId, request)
        return ResponseEntity.ok(team.id)
    }

    @PostMapping("/invite")
    @PreAuthorize("hasRole('MANAGER')")
    fun inviteMember(
        @AuthenticationPrincipal user: User,
        @RequestBody request: InvitationRequest
    ): ResponseEntity<Long> {
        val companyId = user.company?.id ?: throw IllegalStateException("User does not belong to a company")
        val invitation = invitationService.createInvitation(
            companyId, 
            request.email, 
            User.Role.valueOf(request.role)
        )
        return ResponseEntity.ok(invitation.id)
    }
}
