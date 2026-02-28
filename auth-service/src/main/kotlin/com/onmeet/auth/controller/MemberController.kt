package com.onmeet.auth.controller

import com.onmeet.auth.dto.ChangePasswordRequest
import com.onmeet.auth.dto.JobTitleResponse
import com.onmeet.auth.dto.UserProfileUpdateRequest
import com.onmeet.auth.dto.UserResponseDto
import com.onmeet.auth.dto.WithdrawRequest
import com.onmeet.auth.dto.toResponseDto
import com.onmeet.auth.entity.User
import com.onmeet.auth.service.AuthService
import com.onmeet.auth.service.JobTitleService
import com.onmeet.auth.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import com.onmeet.common.dto.ErrorResponse
import com.onmeet.auth.service.TeamService
import com.onmeet.auth.dto.TeamRequest
import org.springframework.security.access.prepost.PreAuthorize
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/v1/member")
@Tag(name = "Member Management", description = "회원 프로필 및 팀 관리 API")
class MemberController(
    private val userService: UserService,
    private val authService: AuthService,
    private val teamService: TeamService,
    private val jobTitleService: JobTitleService
) {

    @Operation(summary = "내 정보 조회", description = "현재 로그인한 자신의 정보를 상세 조회합니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "내 정보 조회 성공",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = UserResponseDto::class)
            )]
        ),
        ApiResponse(
            responseCode = "401",
            description = "인증 실패 - 로그인이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 401, "message": "Authentication failed", "timestamp": 1234567890}"""
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
                    value = """{"status": 500, "message": "Internal server error occurred", "timestamp": 1234567890}"""
                )]
            )]
        )
    ])
    @GetMapping("/me")
    fun getMyInfo(
        @AuthenticationPrincipal user: User
    ): ResponseEntity<UserResponseDto> =
        ResponseEntity.ok(userService.getMyInfo(user))

    @Operation(
        summary = "내 프로필 수정",
        description = "현재 로그인한 자신의 프로필 정보(텍스트 및 이미지)를 수정합니다."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "프로필 수정 성공",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = UserResponseDto::class)
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - 유효하지 않은 데이터, 필수 필드 누락 또는 잘못된 파일 형식",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 400, "message": "Validation failed: name: must not be blank", "timestamp": 1234567890}"""
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
                    value = """{"status": 401, "message": "Authentication failed", "timestamp": 1234567890}"""
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
                    value = """{"status": 500, "message": "Internal server error occurred", "timestamp": 1234567890}"""
                )]
            )]
        )
    ])
    @PatchMapping("/me", consumes = ["multipart/form-data"])
    fun updateProfile(
        @AuthenticationPrincipal user: User,
        @RequestPart("request") @Valid request: UserProfileUpdateRequest,
        @RequestPart(value = "profileImage", required = false) profileImage: MultipartFile?
    ): ResponseEntity<UserResponseDto> =
        ResponseEntity.ok(userService.updateUserProfile(user.requireId(), user, request, profileImage))

    @Operation(summary = "비밀번호 변경", description = "기존 비밀번호 확인 후 새 비밀번호로 변경합니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "비밀번호 변경 성공"
        ),
        ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - 기존 비밀번호가 일치하지 않거나 새 비밀번호 형식이 유효하지 않음",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 400, "message": "Current password is incorrect", "timestamp": 1234567890}"""
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
                    value = """{"status": 401, "message": "Authentication failed", "timestamp": 1234567890}"""
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
                    value = """{"status": 500, "message": "Internal server error occurred", "timestamp": 1234567890}"""
                )]
            )]
        )
    ])
    @PutMapping("/me/password")
    fun changePassword(
        @AuthenticationPrincipal user: User,
        @RequestBody @Valid request: ChangePasswordRequest
    ): ResponseEntity<Void> {
        authService.changePassword(user.email, request)
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "내 프로필 이미지 삭제", description = "현재 로그인한 자신의 프로필 이미지를 삭제합니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "프로필 이미지 삭제 성공 - 기본 이미지로 변경됨",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = UserResponseDto::class)
            )]
        ),
        ApiResponse(
            responseCode = "401",
            description = "인증 실패 - 로그인이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 401, "message": "Authentication failed", "timestamp": 1234567890}"""
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
                    value = """{"status": 500, "message": "Internal server error occurred", "timestamp": 1234567890}"""
                )]
            )]
        )
    ])
    @DeleteMapping("/me/profile-image")
    fun deleteMyProfileImage(
        @AuthenticationPrincipal user: User
    ): ResponseEntity<UserResponseDto> =
        ResponseEntity.ok(userService.deleteMyProfileImage(user))

    @Operation(summary = "회원 탈퇴", description = "비밀번호 검증 후 회원을 탈퇴 처리합니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "204",
            description = "회원 탈퇴 성공 - 응답 본문 없음"
        ),
        ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - 비밀번호가 일치하지 않음",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 400, "message": "Password is incorrect", "timestamp": 1234567890}"""
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
                    value = """{"status": 401, "message": "Authentication failed", "timestamp": 1234567890}"""
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
                    value = """{"status": 500, "message": "Internal server error occurred", "timestamp": 1234567890}"""
                )]
            )]
        )
    ])
    @DeleteMapping("/me")
    fun withdraw(
        @AuthenticationPrincipal user: User,
        @RequestBody @Valid request: WithdrawRequest
    ): ResponseEntity<Void> {
        authService.withdraw(user.email, request)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "회원 정보 조회", description = "특정 회원의 공개 프로필을 조회합니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "회원 정보 조회 성공",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = UserResponseDto::class)
            )]
        ),
        ApiResponse(
            responseCode = "401",
            description = "인증 실패 - 로그인이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 401, "message": "Authentication failed", "timestamp": 1234567890}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "회원을 찾을 수 없음",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 404, "message": "User not found", "timestamp": 1234567890}"""
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
                    value = """{"status": 500, "message": "Internal server error occurred", "timestamp": 1234567890}"""
                )]
            )]
        )
    ])
    @GetMapping("/{memberId}")
    fun getMemberInfo(
        @AuthenticationPrincipal requester: User,
        @PathVariable memberId: Long
    ): ResponseEntity<UserResponseDto> =
        ResponseEntity.ok(userService.getUserInfo(memberId, requester))

    @Operation(summary = "팀 생성 요청", description = "팀 생성을 요청합니다. 일반 직원은 승인 대기, 매니저는 즉시 생성됩니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "팀 생성 요청 성공 - 생성된 팀 ID 반환 (일반 직원은 승인 대기, 매니저는 즉시 생성)",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = Long::class)
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - 필수 필드 누락 또는 유효하지 않은 데이터",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 400, "message": "Validation failed: name: must not be blank", "timestamp": 1234567890}"""
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
                    value = """{"status": 401, "message": "Authentication failed", "timestamp": 1234567890}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "409",
            description = "충돌 - 동일한 이름의 팀이 이미 존재함",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 409, "message": "Team already exists", "timestamp": 1234567890}"""
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
                    value = """{"status": 500, "message": "Internal server error occurred", "timestamp": 1234567890}"""
                )]
            )]
        )
    ])
    @PostMapping("/teams")
    fun createTeam(
        @AuthenticationPrincipal user: User,
        @RequestBody request: TeamRequest
    ): ResponseEntity<Long> =
        ResponseEntity.ok(teamService.createTeam(user, request).id)

    @Operation(summary = "팀장 위임", description = "팀장직을 다른 팀원에게 위임합니다 (현 팀장 또는 매니저 가능).")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "팀장 위임 성공"
        ),
        ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - 팀에 속하지 않은 사용자에게 위임 시도",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 400, "message": "User is not a member of this team", "timestamp": 1234567890}"""
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
                    value = """{"status": 401, "message": "Authentication failed", "timestamp": 1234567890}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "403",
            description = "권한 없음 - 팀장 또는 매니저 권한이 필요함",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 403, "message": "Access denied", "timestamp": 1234567890}"""
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
                    value = """{"status": 404, "message": "Team not found", "timestamp": 1234567890}"""
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
                    value = """{"status": 500, "message": "Internal server error occurred", "timestamp": 1234567890}"""
                )]
            )]
        )
    ])
    @PreAuthorize("hasRole('MANAGER') or @teamSecurity.isLeaderOf(#teamId, principal)")
    @PostMapping("/teams/{teamId}/delegate/{userId}")
    fun delegateLeader(
        @AuthenticationPrincipal user: User,
        @PathVariable teamId: Long,
        @PathVariable userId: Long
    ): ResponseEntity<Void> =
        teamService.delegateLeader(teamId, user, userId).let { ResponseEntity.ok().build() }

    @Operation(summary = "팀 해체", description = "팀을 삭제합니다 (팀장 또는 매니저 가능).")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "팀 해체 성공"
        ),
        ApiResponse(
            responseCode = "401",
            description = "인증 실패 - 로그인이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 401, "message": "Authentication failed", "timestamp": 1234567890}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "403",
            description = "권한 없음 - 팀장 또는 매니저 권한이 필요함",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 403, "message": "Access denied", "timestamp": 1234567890}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "팀을 찾을 수 없음",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 404, "message": "Team not found", "timestamp": 1234567890}"""
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
                    value = """{"status": 500, "message": "Internal server error occurred", "timestamp": 1234567890}"""
                )]
            )]
        )
    ])
    @PreAuthorize("hasRole('MANAGER') or @teamSecurity.isLeaderOf(#teamId, principal)")
    @DeleteMapping("/teams/{teamId}")
    fun dissolveTeam(
        @AuthenticationPrincipal user: User,
        @PathVariable teamId: Long
    ): ResponseEntity<Void> =
        teamService.dissolveTeam(teamId, user).let { ResponseEntity.ok().build() }

    @Operation(summary = "팀 생성 요청 취소", description = "자신이 요청한 팀 생성을 취소합니다 (승인 대기 상태에서만 가능).")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "팀 생성 요청 취소 성공"
        ),
        ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - 이미 승인된 팀이거나 취소할 수 없는 상태",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 400, "message": "Team is already approved", "timestamp": 1234567890}"""
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
                    value = """{"status": 401, "message": "Authentication failed", "timestamp": 1234567890}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "403",
            description = "권한 없음 - 요청자 본인이 아님",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 403, "message": "Access denied", "timestamp": 1234567890}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "팀을 찾을 수 없음",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 404, "message": "Team not found", "timestamp": 1234567890}"""
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
                    value = """{"status": 500, "message": "Internal server error occurred", "timestamp": 1234567890}"""
                )]
            )]
        )
    ])
    @DeleteMapping("/teams/{teamId}/cancel")
    fun cancelTeamRequest(
        @AuthenticationPrincipal user: User,
        @PathVariable teamId: Long
    ): ResponseEntity<Void> =
        teamService.cancelTeamRequest(teamId, user).let { ResponseEntity.ok().build() }

    @Operation(summary = "직급 목록 조회", description = "현재 소속된 회사의 모든 직급 목록을 조회합니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "직급 목록 조회 성공",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = JobTitleResponse::class)
            )]
        ),
        ApiResponse(
            responseCode = "401",
            description = "인증 실패 - 로그인이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 401, "message": "Authentication failed", "timestamp": 1234567890}"""
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
                    value = """{"status": 500, "message": "Internal server error occurred", "timestamp": 1234567890}"""
                )]
            )]
        )
    ])
    @GetMapping("/job-titles")
    fun getJobTitles(@AuthenticationPrincipal user: User): ResponseEntity<List<JobTitleResponse>> =
        jobTitleService.getJobTitles(user.company)
            .map { it.toResponseDto() }
            .let { ResponseEntity.ok(it) }
}
