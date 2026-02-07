package com.onmeet.auth.controller

import com.onmeet.auth.dto.InvitationRequest
import com.onmeet.auth.dto.TeamRequest
import com.onmeet.auth.entity.User
import com.onmeet.auth.service.CompanyService
import com.onmeet.auth.service.InvitationService
import com.onmeet.auth.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
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
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "팀 생성 성공",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = Long::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (팀 이름 중복 등)",
                content = [Content(
                    mediaType = "application/json",
                    examples = [ExampleObject(value = "{\"error\": \"Team already exists in this company\"}")]
                )]
            ),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음 (매니저 아님)",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "404",
                description = "기업을 찾을 수 없음",
                content = [Content(
                    mediaType = "application/json",
                    examples = [ExampleObject(value = "{\"error\": \"Company not found\"}")]
                )]
            )
        ]
    )
    @PostMapping("/teams")
    @PreAuthorize("hasRole('MANAGER')")
    fun createTeam(
        @AuthenticationPrincipal user: User,
        @RequestBody request: TeamRequest
    ): ResponseEntity<Long> {
        val companyId = user.company.id ?: throw IllegalStateException("User is not associated with a company")
        val team = companyService.createTeam(companyId, request)
        return ResponseEntity.ok(team.id)
    }

    @Operation(summary = "멤버 초대", description = "이메일로 새로운 멤버를 기업에 초대합니다 (매니저 권한 필요).")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "초대 발송 성공",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = Long::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (이미 초대됨 등)",
                content = [Content(
                    mediaType = "application/json",
                    examples = [ExampleObject(value = "{\"error\": \"Active invitation already exists\"}")]
                )]
            ),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음 (매니저 아님)",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "404",
                description = "기업을 찾을 수 없음",
                content = [Content(
                    mediaType = "application/json",
                    examples = [ExampleObject(value = "{\"error\": \"Company not found\"}")]
                )]
            )
        ]
    )
    @PostMapping("/invite")
    @PreAuthorize("hasRole('MANAGER')")
    fun inviteMember(
        @AuthenticationPrincipal user: User,
        @RequestBody request: InvitationRequest
    ): ResponseEntity<Long> {
        val companyId = user.company.id ?: throw IllegalStateException("User is not associated with a company")
        val invitation = invitationService.createInvitation(
            companyId, 
            request.email, 
            request.role
        )
        return ResponseEntity.ok(invitation.id)
    }
}
