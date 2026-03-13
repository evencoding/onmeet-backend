package com.onmeet.auth.controller

import com.onmeet.auth.dto.*
import com.onmeet.auth.service.TeamService
import com.onmeet.common.dto.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/internal/teams")
@Tag(name = "Internal Team API", description = "내부 서비스 간 팀 정보 조회 API (Gateway 인증 필요)")
class InternalTeamController(
    private val teamService: TeamService
) {

    @Operation(
        summary = "팀 존재 여부 확인",
        description = "단일 팀의 존재 여부를 확인합니다. (내부 서비스 전용)"
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "팀 존재 여부 확인 성공",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = TeamExistsResponse::class)
            )]
        ),
        ApiResponse(
            responseCode = "401",
            description = "내부 인증 실패 - 유효하지 않은 Gateway Secret",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_046","status":401,"message":"유효하지 않은 내부 인증 시크릿입니다","timestamp":1710000000000}"""
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
    @GetMapping("/{teamId}/exists")
    fun checkTeamExists(
        @PathVariable teamId: Long
    ): ResponseEntity<TeamExistsResponse> {
        val exists = teamService.teamExists(teamId)
        return ResponseEntity.ok(TeamExistsResponse(teamId, exists))
    }

    @Operation(
        summary = "팀 멤버십 확인",
        description = "사용자가 특정 팀의 멤버인지 확인합니다. (내부 서비스 전용)"
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "팀 멤버십 확인 성공",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = TeamMembershipResponse::class)
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - teamId 또는 userId 필드 누락",
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
            description = "내부 인증 실패 - 유효하지 않은 Gateway Secret",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_046","status":401,"message":"유효하지 않은 내부 인증 시크릿입니다","timestamp":1710000000000}"""
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
    @PostMapping("/membership/check")
    fun checkTeamMembership(
        @RequestBody request: TeamMembershipRequest
    ): ResponseEntity<TeamMembershipResponse> {
        val isMember = teamService.isTeamMember(request.teamId, request.userId)
        return ResponseEntity.ok(
            TeamMembershipResponse(
                teamId = request.teamId,
                userId = request.userId,
                isMember = isMember
            )
        )
    }
}
