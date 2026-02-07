package com.onmeet.auth.controller

import com.onmeet.auth.dto.InvitationRequest
import com.onmeet.auth.dto.TeamRequest
import com.onmeet.auth.entity.User
import com.onmeet.auth.service.CompanyService
import com.onmeet.auth.service.InvitationService
import com.onmeet.auth.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/companies")
@Tag(name = "Company & Team", description = "기업 및 팀 관리 API")
class CompanyController(
    private val companyService: CompanyService,
    private val invitationService: InvitationService,
    private val userService: UserService
) {

    @Operation(summary = "팀 생성", description = "기업 내에 새로운 팀을 생성합니다 (매니저 권한 필요).")
    @PostMapping("/teams")
    @PreAuthorize("hasRole('MANAGER')")
    fun createTeam(
        @AuthenticationPrincipal user: User,
        @RequestBody request: TeamRequest
    ): ResponseEntity<Long> {
        val companyId = user.company?.id ?: throw IllegalStateException("User is not associated with a company")
        val team = companyService.createTeam(companyId, request)
        return ResponseEntity.ok(team.id)
    }

    @Operation(summary = "멤버 초대", description = "이메일로 새로운 멤버를 기업에 초대합니다 (매니저 권한 필요).")
    @PostMapping("/invite")
    @PreAuthorize("hasRole('MANAGER')")
    fun inviteMember(
        @AuthenticationPrincipal user: User,
        @RequestBody request: InvitationRequest
    ): ResponseEntity<Long> {
        val companyId = user.company?.id ?: throw IllegalStateException("User is not associated with a company")
        val invitation = invitationService.createInvitation(
            companyId, 
            request.email, 
            request.role
        )
        return ResponseEntity.ok(invitation.id)
    }
}
