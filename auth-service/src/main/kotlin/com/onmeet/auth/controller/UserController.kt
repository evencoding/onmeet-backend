package com.onmeet.auth.controller

import com.onmeet.auth.dto.PageResponse
import com.onmeet.auth.dto.UserProfileUpdateRequest
import com.onmeet.auth.dto.UserResponseDto
import com.onmeet.auth.entity.User
import com.onmeet.auth.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.ExampleObject
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/users")
@Tag(name = "User Management", description = "사용자 프로필 및 정보 관리 API")
class UserController(
    private val userService: UserService
) {

    @Operation(summary = "전체 사원 목록 조회", description = "현재 기업의 모든 사원 목록을 페이징하여 조회합니다 (매니저 권한 필요).")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(responseCode = "403", description = "권한 없음 (매니저 아님)")
    ])
    @GetMapping("/employees")
    fun getAllEmployees(
        @AuthenticationPrincipal user: User,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<PageResponse<UserResponseDto>> =
        ResponseEntity.ok(userService.getAllEmployees(user, pageable))

    @Operation(summary = "사용자 정보 조회", description = "특정 사용자의 정보를 상세 조회합니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음", content = [Content(examples = [ExampleObject(value = "{\"error\": \"User not found\"}")])])
    ])
    @GetMapping("/{userId}")
    fun getUserInfo(
        @AuthenticationPrincipal requester: User,
        @PathVariable userId: Long
    ): ResponseEntity<UserResponseDto> =
        ResponseEntity.ok(userService.getUserInfo(userId, requester))

    @Operation(summary = "내 프로필 수정", description = "현재 로그인한 자신의 프로필 정보를 수정합니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "수정 성공 (수정된 정보 반환)"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (유효성 검사 실패 등)")
    ])
    @PutMapping("/{userId}")
    fun updateUserProfile(
        @AuthenticationPrincipal user: User,
        @PathVariable userId: Long,
        @RequestBody request: UserProfileUpdateRequest
    ): ResponseEntity<UserResponseDto> =
        ResponseEntity.ok(userService.updateUserProfile(userId, user, request))

    @Operation(summary = "사용자 비활성화", description = "특정 사용자의 계정을 비활성화합니다 (동일 회사 매니저 권한 필요).")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "비활성화 성공"),
        ApiResponse(responseCode = "403", description = "권한 없음 (매니저 아님 또는 타사 사용자)")
    ])
    @PutMapping("/{userId}/deactivate")
    fun deactivateUser(
        @AuthenticationPrincipal manager: User,
        @PathVariable userId: Long
    ): ResponseEntity<UserResponseDto> =
        ResponseEntity.ok(userService.deactivateUser(userId, manager))

    @Operation(summary = "사용자 활성화", description = "특정 사용자의 계정을 활성화합니다 (동일 회사 매니저 권한 필요).")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "활성화 성공"),
        ApiResponse(responseCode = "403", description = "권한 없음 (매니저 아님 또는 타사 사용자)")
    ])
    @PutMapping("/{userId}/activate")
    fun activateUser(
        @AuthenticationPrincipal manager: User,
        @PathVariable userId: Long
    ): ResponseEntity<UserResponseDto> =
        ResponseEntity.ok(userService.activateUser(userId, manager))

    @Operation(summary = "[내부용] 사용자 권한 정보 조회", description = "파일 서비스 등 타 서비스에서 권한 검증을 위해 사용자의 역할, 회사, 팀 정보를 조회합니다.")
    @GetMapping("/internal/{userId}/permissions")
    fun getUserPermissions(@PathVariable userId: Long): ResponseEntity<com.onmeet.auth.dto.UserPermissionResponse> =
        ResponseEntity.ok(userService.getUserPermissions(userId))

    @Operation(summary = "[내부용] 사용자 팀 정보 조회")
    @GetMapping("/internal/{userId}/teams")
    fun getUserTeams(@PathVariable userId: Long): ResponseEntity<List<com.onmeet.auth.dto.TeamInfoDto>> =
        ResponseEntity.ok(userService.getUserPermissions(userId).teamIds.map { teamId ->
            // Simple mapping or better - add a service method if needed. 
            // For now, mapping from the aggregated permission response is efficient.
            com.onmeet.auth.dto.TeamInfoDto(id = teamId, name = "Team $teamId", color = null)
        })

    @Operation(summary = "[내부용] 사용자 회사 정보 조회")
    @GetMapping("/internal/{userId}/company")
    fun getUserCompany(@PathVariable userId: Long): ResponseEntity<com.onmeet.auth.dto.CompanyInfoDto> =
        userService.getUserPermissions(userId).let { 
            ResponseEntity.ok(com.onmeet.auth.dto.CompanyInfoDto(id = it.companyId ?: 0L, name = "Company ${it.companyId}"))
        }
}
