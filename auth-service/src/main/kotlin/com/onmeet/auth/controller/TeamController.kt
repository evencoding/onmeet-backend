package com.onmeet.auth.controller

import com.onmeet.auth.dto.TeamRequest
import com.onmeet.auth.entity.User
import com.onmeet.auth.service.TeamService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/teams")
@Tag(name = "Team Management", description = "팀 관리 API (생성, 승인, 위임, 해체)")
class TeamController(
    private val teamService: TeamService
) {

    @Operation(summary = "팀 생성 요청", description = "팀 생성을 요청합니다. 일반 직원은 승인 대기, 매니저는 즉시 생성됩니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "팀 생성 성공 (Team ID 반환)", content = [Content(schema = Schema(implementation = Long::class))]),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (팀 이름 중복 등)", content = [Content(examples = [ExampleObject(value = "{\"error\": \"Team already exists\"}")])]),
        ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    ])
    @PostMapping
    fun createTeam(
        @AuthenticationPrincipal user: User,
        @RequestBody request: TeamRequest
    ): ResponseEntity<Long> =
        ResponseEntity.ok(teamService.createTeam(user, request).id)

    @Operation(summary = "팀 승인", description = "팀 생성 요청을 승인합니다 (매니저 전용).")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "승인 성공"),
        ApiResponse(responseCode = "403", description = "권한 없음 (매니저 아님)"),
        ApiResponse(responseCode = "404", description = "팀 또는 사용자를 찾을 수 없음")
    ])
    @PostMapping("/{teamId}/approve")
    fun approveTeam(
        @AuthenticationPrincipal user: User,
        @PathVariable teamId: Long
    ): ResponseEntity<Void> =
        teamService.approveTeam(teamId, user).let { ResponseEntity.ok().build() }
    
    @Operation(summary = "팀 반려", description = "팀 생성 요청을 반려(삭제)합니다 (매니저 전용).")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "반려 성공"),
        ApiResponse(responseCode = "403", description = "권한 없음 (매니저 아님)"),
        ApiResponse(responseCode = "404", description = "팀 또는 사용자를 찾을 수 없음")
    ])
    @PostMapping("/{teamId}/reject")
    fun rejectTeam(
        @AuthenticationPrincipal user: User,
        @PathVariable teamId: Long
    ): ResponseEntity<Void> =
        teamService.rejectTeam(teamId, user).let { ResponseEntity.ok().build() }

    @Operation(summary = "팀장 임명", description = "특정 사용자에게 팀장 권한을 부여합니다 (매니저 전용).")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "임명 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (팀에 속하지 않은 사용자 등)"),
        ApiResponse(responseCode = "403", description = "권한 없음 (매니저 아님)"),
        ApiResponse(responseCode = "404", description = "팀 또는 사용자를 찾을 수 없음")
    ])
    @PostMapping("/{teamId}/leader/{userId}")
    fun assignLeader(
        @AuthenticationPrincipal user: User,
        @PathVariable teamId: Long,
        @PathVariable userId: Long
    ): ResponseEntity<Void> =
        teamService.assignLeader(teamId, user, userId).let { ResponseEntity.ok().build() }
    
    @Operation(summary = "팀장 위임", description = "팀장직을 다른 팀원에게 위임합니다 (현 팀장 또는 매니저 가능).")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "위임 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (팀에 속하지 않은 사용자 등)"),
        ApiResponse(responseCode = "403", description = "권한 없음 (팀장 또는 매니저 아님)"),
        ApiResponse(responseCode = "404", description = "팀 또는 사용자를 찾을 수 없음")
    ])
    @PostMapping("/{teamId}/delegate/{userId}")
    fun delegateLeader(
        @AuthenticationPrincipal user: User,
        @PathVariable teamId: Long,
        @PathVariable userId: Long
    ): ResponseEntity<Void> =
        teamService.delegateLeader(teamId, user, userId).let { ResponseEntity.ok().build() }

    @Operation(summary = "팀 해체", description = "팀을 삭제합니다 (팀장 또는 매니저 가능).")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "해체 성공"),
        ApiResponse(responseCode = "403", description = "권한 없음 (팀장 또는 매니저 아님)"),
        ApiResponse(responseCode = "404", description = "팀 또는 사용자를 찾을 수 없음")
    ])
    @DeleteMapping("/{teamId}")
    fun dissolveTeam(
        @AuthenticationPrincipal user: User,
        @PathVariable teamId: Long
    ): ResponseEntity<Void> =
        teamService.dissolveTeam(teamId, user).let { ResponseEntity.ok().build() }
}
