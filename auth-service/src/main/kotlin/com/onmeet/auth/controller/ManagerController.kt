package com.onmeet.auth.controller

import com.onmeet.auth.dto.CompanyResponse
import com.onmeet.auth.dto.InvitationRequest
import com.onmeet.auth.dto.SingleInvitationRequest
import com.onmeet.auth.dto.JobTitleRequest
import com.onmeet.auth.dto.JobTitleResponse
import com.onmeet.auth.dto.PageResponse
import com.onmeet.auth.dto.TeamRejectRequest
import com.onmeet.auth.dto.UpdateCompanyRequest
import com.onmeet.auth.dto.UserResponseDto
import com.onmeet.auth.dto.toResponseDto
import com.onmeet.auth.entity.User
import com.onmeet.auth.service.AuthService
import com.onmeet.auth.service.CompanyService
import com.onmeet.auth.service.InvitationService
import com.onmeet.auth.service.JobTitleService
import com.onmeet.auth.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.ExampleObject
import com.onmeet.common.dto.ErrorResponse
import com.onmeet.auth.service.TeamService
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/manager")
@Tag(name = "Manager Management", description = "기업 관리자 전용 API")
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
class ManagerController(
    private val userService: UserService,
    private val authService: AuthService,
    private val teamService: TeamService,
    private val invitationService: InvitationService,
    private val jobTitleService: JobTitleService,
    private val companyService: CompanyService
) {

    @Operation(summary = "회사 정보 수정", description = "현재 로그인한 관리자의 회사 정보를 수정합니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "회사 정보 수정 성공",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = CompanyResponse::class)
            )]
        ),
        ApiResponse(
            responseCode = "401",
            description = "인증 실패 - 로그인이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_004","status":401,"message":"인증에 실패했습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "403",
            description = "권한 없음 - MANAGER 또는 ADMIN 권한이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_ACCESS_DENIED","status":403,"message":"접근이 거부되었습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "회사를 찾을 수 없음",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_035","status":404,"message":"해당 회사를 찾을 수 없습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_INTERNAL_ERROR","status":500,"message":"서버 내부 오류가 발생했습니다","timestamp":1710000000000}"""
                )]
            )]
        )
    ])
    @PatchMapping("/company")
    fun updateCompany(
        @AuthenticationPrincipal user: User,
        @RequestBody @jakarta.validation.Valid request: UpdateCompanyRequest
    ): ResponseEntity<CompanyResponse> {
        val updated = companyService.updateCompany(user.company.requireId(), request)
        return ResponseEntity.ok(CompanyResponse(
            id = updated.requireId(),
            name = updated.name,
            status = updated.status.name
        ))
    }

    @Operation(summary = "전체 사원 목록 조회", description = "현재 기업의 모든 사원 목록을 페이징하여 조회합니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "사원 목록 조회 성공",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = PageResponse::class)
            )]
        ),
        ApiResponse(
            responseCode = "401",
            description = "인증 실패 - 로그인이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_004","status":401,"message":"인증에 실패했습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "403",
            description = "권한 없음 - MANAGER 또는 ADMIN 권한이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_ACCESS_DENIED","status":403,"message":"접근이 거부되었습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_INTERNAL_ERROR","status":500,"message":"서버 내부 오류가 발생했습니다","timestamp":1710000000000}"""
                )]
            )]
        )
    ])
    @GetMapping("/employees")
    fun getAllEmployees(
        @AuthenticationPrincipal user: User,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<PageResponse<UserResponseDto>> =
        ResponseEntity.ok(userService.getAllEmployees(user, pageable))

    @Operation(summary = "사원 계정 비활성화", description = "특정 사원의 계정을 비활성화합니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "사원 계정 비활성화 성공",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = UserResponseDto::class)
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - 유효하지 않은 사용자 ID",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_VALIDATION_FAILED","status":400,"message":"입력값 검증 실패: 유효하지 않은 데이터입니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "401",
            description = "인증 실패 - 로그인이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_004","status":401,"message":"인증에 실패했습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "403",
            description = "권한 없음 - MANAGER 또는 ADMIN 권한이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_ACCESS_DENIED","status":403,"message":"접근이 거부되었습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "사용자를 찾을 수 없음",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_006","status":404,"message":"해당 사용자를 찾을 수 없습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_INTERNAL_ERROR","status":500,"message":"서버 내부 오류가 발생했습니다","timestamp":1710000000000}"""
                )]
            )]
        )
    ])
    @PutMapping("/employees/{userId}/deactivate")
    fun deactivateUser(
        @AuthenticationPrincipal manager: User,
        @PathVariable userId: Long
    ): ResponseEntity<UserResponseDto> =
        ResponseEntity.ok(userService.deactivateUser(userId, manager))

    @Operation(summary = "사원 계정 활성화", description = "특정 사원의 계정을 활성화합니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "사원 계정 활성화 성공",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = UserResponseDto::class)
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - 유효하지 않은 사용자 ID",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_VALIDATION_FAILED","status":400,"message":"입력값 검증 실패: 유효하지 않은 데이터입니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "401",
            description = "인증 실패 - 로그인이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_004","status":401,"message":"인증에 실패했습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "403",
            description = "권한 없음 - MANAGER 또는 ADMIN 권한이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_ACCESS_DENIED","status":403,"message":"접근이 거부되었습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "사용자를 찾을 수 없음",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_006","status":404,"message":"해당 사용자를 찾을 수 없습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_INTERNAL_ERROR","status":500,"message":"서버 내부 오류가 발생했습니다","timestamp":1710000000000}"""
                )]
            )]
        )
    ])
    @PutMapping("/employees/{userId}/activate")
    fun activateUser(
        @AuthenticationPrincipal manager: User,
        @PathVariable userId: Long
    ): ResponseEntity<UserResponseDto> =
        ResponseEntity.ok(userService.activateUser(userId, manager))

    @Operation(summary = "사원 프로필 이미지 초기화", description = "특정 사원의 프로필 이미지를 기본 이미지로 리셋합니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "204",
            description = "프로필 이미지 초기화 성공 - 응답 본문 없음"
        ),
        ApiResponse(
            responseCode = "401",
            description = "인증 실패 - 로그인이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_004","status":401,"message":"인증에 실패했습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "403",
            description = "권한 없음 - MANAGER 또는 ADMIN 권한이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_ACCESS_DENIED","status":403,"message":"접근이 거부되었습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "사용자를 찾을 수 없음",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_006","status":404,"message":"해당 사용자를 찾을 수 없습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_INTERNAL_ERROR","status":500,"message":"서버 내부 오류가 발생했습니다","timestamp":1710000000000}"""
                )]
            )]
        )
    ])
    @DeleteMapping("/employees/{userId}/profile-image")
    fun resetProfileImage(
        @PathVariable userId: Long,
        authentication: Authentication
    ): ResponseEntity<Void> {
        val requesterEmail = authentication.name
        authService.resetUserProfileImage(userId, requesterEmail)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "팀 승인", description = "팀 생성 요청을 승인합니다 (매니저 전용).")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "팀 승인 성공"
        ),
        ApiResponse(
            responseCode = "401",
            description = "인증 실패 - 로그인이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_004","status":401,"message":"인증에 실패했습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "403",
            description = "권한 없음 - MANAGER 권한이 필요하거나 같은 기업에 속하지 않음",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_ACCESS_DENIED","status":403,"message":"접근이 거부되었습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "팀 또는 사용자를 찾을 수 없음",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_025","status":404,"message":"해당 팀을 찾을 수 없습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_INTERNAL_ERROR","status":500,"message":"서버 내부 오류가 발생했습니다","timestamp":1710000000000}"""
                )]
            )]
        )
    ])
    @PreAuthorize("hasRole('MANAGER') and @teamSecurity.belongsToSameCompany(#teamId, principal)")
    @PostMapping("/teams/{teamId}/approve")
    fun approveTeam(
        @AuthenticationPrincipal user: User,
        @PathVariable teamId: Long
    ): ResponseEntity<Void> =
        teamService.approveTeam(teamId, user).let { ResponseEntity.ok().build() }

    @Operation(summary = "팀 반려", description = "팀 생성 요청을 반려합니다 (매니저 전용). 사유를 입력할 수 있습니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "팀 반려 성공"
        ),
        ApiResponse(
            responseCode = "401",
            description = "인증 실패 - 로그인이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_004","status":401,"message":"인증에 실패했습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "403",
            description = "권한 없음 - MANAGER 권한이 필요하거나 같은 기업에 속하지 않음",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_ACCESS_DENIED","status":403,"message":"접근이 거부되었습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "팀 또는 사용자를 찾을 수 없음",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_025","status":404,"message":"해당 팀을 찾을 수 없습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_INTERNAL_ERROR","status":500,"message":"서버 내부 오류가 발생했습니다","timestamp":1710000000000}"""
                )]
            )]
        )
    ])
    @PreAuthorize("hasRole('MANAGER') and @teamSecurity.belongsToSameCompany(#teamId, principal)")
    @PostMapping("/teams/{teamId}/reject")
    fun rejectTeam(
        @AuthenticationPrincipal user: User,
        @PathVariable teamId: Long,
        @RequestBody(required = false) request: TeamRejectRequest?
    ): ResponseEntity<Void> =
        teamService.rejectTeam(teamId, user, request?.reason).let { ResponseEntity.ok().build() }

    @Operation(summary = "팀장 임명", description = "특정 사용자에게 팀장 권한을 부여합니다 (매니저 전용).")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "팀장 임명 성공"
        ),
        ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - 팀에 속하지 않은 사용자이거나 유효하지 않은 요청",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_031","status":400,"message":"현재 리더가 팀 멤버가 아닙니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "401",
            description = "인증 실패 - 로그인이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_004","status":401,"message":"인증에 실패했습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "403",
            description = "권한 없음 - MANAGER 권한이 필요하거나 같은 기업에 속하지 않음",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_ACCESS_DENIED","status":403,"message":"접근이 거부되었습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "팀 또는 사용자를 찾을 수 없음",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_025","status":404,"message":"해당 팀을 찾을 수 없습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_INTERNAL_ERROR","status":500,"message":"서버 내부 오류가 발생했습니다","timestamp":1710000000000}"""
                )]
            )]
        )
    ])
    @PreAuthorize("hasRole('MANAGER') and @teamSecurity.belongsToSameCompany(#teamId, principal)")
    @PostMapping("/teams/{teamId}/leader/{userId}")
    fun assignLeader(
        @AuthenticationPrincipal user: User,
        @PathVariable teamId: Long,
        @PathVariable userId: Long
    ): ResponseEntity<Void> =
        teamService.assignLeader(teamId, user, userId).let { ResponseEntity.ok().build() }

    @Operation(summary = "멤버 초대", description = "이메일 리스트로 새로운 멤버를 기업에 초대합니다 (매니저 권한 필요). 역할은 USER로 고정됩니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "초대 발송 성공 - 생성된 초대 ID 리스트 반환",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = List::class)
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - 유효하지 않은 이메일 형식 또는 빈 리스트",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_VALIDATION_FAILED","status":400,"message":"입력값 검증 실패: email: 유효한 이메일이 아닙니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "401",
            description = "인증 실패 - 로그인이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_004","status":401,"message":"인증에 실패했습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "403",
            description = "권한 없음 - MANAGER 권한이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_ACCESS_DENIED","status":403,"message":"접근이 거부되었습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "기업을 찾을 수 없음",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_035","status":404,"message":"해당 회사를 찾을 수 없습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "409",
            description = "충돌 - 이미 활성화된 초대가 존재함",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_037","status":409,"message":"해당 이메일로 이미 유효한 초대가 존재합니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_INTERNAL_ERROR","status":500,"message":"서버 내부 오류가 발생했습니다","timestamp":1710000000000}"""
                )]
            )]
        )
    ])
    @PostMapping("/invite")
    @PreAuthorize("hasRole('MANAGER')")
    fun inviteMember(
        @AuthenticationPrincipal user: User,
        @RequestBody @jakarta.validation.Valid request: InvitationRequest
    ): ResponseEntity<List<Long>> {
        val invitationIds = request.emails.map { email ->
            invitationService.createInvitation(user.company.requireId(), email, User.Role.USER).requireId()
        }
        return ResponseEntity.ok(invitationIds)
    }

    @Operation(summary = "단일 멤버 초대 (역할 지정)", description = "이메일과 역할을 지정하여 단일 멤버를 초대합니다 (매니저 권한 필요).")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "초대 발송 성공 - 생성된 초대 ID 반환"),
        ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - 유효하지 않은 이메일 또는 역할",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "401",
            description = "인증 실패",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "403",
            description = "권한 없음 - MANAGER 권한 필요",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "409",
            description = "충돌 - 이미 활성화된 초대가 존재함",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
        )
    ])
    @PostMapping("/invite/single")
    @PreAuthorize("hasRole('MANAGER')")
    fun inviteSingleMember(
        @AuthenticationPrincipal user: User,
        @RequestBody @jakarta.validation.Valid request: SingleInvitationRequest
    ): ResponseEntity<Long> {
        val role = try {
            User.Role.valueOf(request.role)
        } catch (e: IllegalArgumentException) {
            throw org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid role: ${request.role}"
            )
        }
        val invitationId = invitationService.createInvitation(user.company.requireId(), request.email, role).requireId()
        return ResponseEntity.ok(invitationId)
    }

    @Operation(summary = "직급 생성", description = "새로운 직급을 생성합니다 (매니저 전용).")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "직급 생성 성공",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = JobTitleResponse::class)
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - 유효하지 않은 직급 정보",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_VALIDATION_FAILED","status":400,"message":"입력값 검증 실패: 유효하지 않은 직급 데이터입니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "401",
            description = "인증 실패 - 로그인이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_004","status":401,"message":"인증에 실패했습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "403",
            description = "권한 없음 - MANAGER 또는 ADMIN 권한이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_ACCESS_DENIED","status":403,"message":"접근이 거부되었습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_INTERNAL_ERROR","status":500,"message":"서버 내부 오류가 발생했습니다","timestamp":1710000000000}"""
                )]
            )]
        )
    ])
    @PostMapping("/job-titles")
    fun createJobTitle(
        @AuthenticationPrincipal user: User,
        @RequestBody request: JobTitleRequest
    ): ResponseEntity<JobTitleResponse> =
        jobTitleService.createJobTitle(user, request)
            .let { ResponseEntity.ok(it.toResponseDto()) }

    @Operation(summary = "직급 수정", description = "기존 직급 정보를 수정합니다 (매니저 전용).")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "직급 수정 성공",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = JobTitleResponse::class)
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - 유효하지 않은 직급 정보",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_VALIDATION_FAILED","status":400,"message":"입력값 검증 실패: 유효하지 않은 직급 데이터입니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "401",
            description = "인증 실패 - 로그인이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_004","status":401,"message":"인증에 실패했습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "403",
            description = "권한 없음 - MANAGER 또는 ADMIN 권한이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_ACCESS_DENIED","status":403,"message":"접근이 거부되었습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "직급을 찾을 수 없음",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_014","status":404,"message":"해당 직급을 찾을 수 없습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_INTERNAL_ERROR","status":500,"message":"서버 내부 오류가 발생했습니다","timestamp":1710000000000}"""
                )]
            )]
        )
    ])
    @PutMapping("/job-titles/{id}")
    fun updateJobTitle(
        @AuthenticationPrincipal user: User,
        @PathVariable id: Long,
        @RequestBody request: JobTitleRequest
    ): ResponseEntity<JobTitleResponse> =
        jobTitleService.updateJobTitle(user, id, request)
            .let { ResponseEntity.ok(it.toResponseDto()) }

    @Operation(summary = "직급 삭제", description = "직급을 삭제합니다 (매니저 전용).")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "직급 삭제 성공"
        ),
        ApiResponse(
            responseCode = "401",
            description = "인증 실패 - 로그인이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_004","status":401,"message":"인증에 실패했습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "403",
            description = "권한 없음 - MANAGER 또는 ADMIN 권한이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_ACCESS_DENIED","status":403,"message":"접근이 거부되었습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "직급을 찾을 수 없음",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_014","status":404,"message":"해당 직급을 찾을 수 없습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_INTERNAL_ERROR","status":500,"message":"서버 내부 오류가 발생했습니다","timestamp":1710000000000}"""
                )]
            )]
        )
    ])
    @DeleteMapping("/job-titles/{id}")
    fun deleteJobTitle(
        @AuthenticationPrincipal user: User,
        @PathVariable id: Long
    ): ResponseEntity<Void> =
        jobTitleService.deleteJobTitle(user, id)
            .let { ResponseEntity.ok().build() }
}
