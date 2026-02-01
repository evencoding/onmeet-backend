package com.onmeet.auth.controller

import com.onmeet.auth.dto.InvitationRequest
import com.onmeet.auth.dto.TeamRequest
import com.onmeet.auth.entity.User
import com.onmeet.auth.service.CompanyService
import com.onmeet.auth.service.InvitationService
import com.onmeet.auth.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/companies")
class CompanyController(
    private val companyService: CompanyService,
    private val invitationService: InvitationService,
    private val userService: UserService
) {

    @PostMapping("/teams")
    @PreAuthorize("hasRole('MANAGER')")
    fun createTeam(
        @AuthenticationPrincipal user: User,
        @RequestBody request: TeamRequest
    ): ResponseEntity<Long> {
        val userId = user.id ?: throw IllegalStateException("User ID missing from principal")
        val companyId = userService.getCompanyIdByUserId(userId)
        val team = companyService.createTeam(companyId, request)
        return ResponseEntity.ok(team.id)
    }

    @PostMapping("/invite")
    @PreAuthorize("hasRole('MANAGER')")
    fun inviteMember(
        @AuthenticationPrincipal user: User,
        @RequestBody request: InvitationRequest
    ): ResponseEntity<Long> {
        val userId = user.id ?: throw IllegalStateException("User ID missing from principal")
        val companyId = userService.getCompanyIdByUserId(userId)
        val invitation = invitationService.createInvitation(
            companyId, 
            request.email, 
            request.role
        )
        return ResponseEntity.ok(invitation.id)
    }
}
