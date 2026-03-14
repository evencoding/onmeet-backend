package com.onmeet.auth.controller.internal

import com.onmeet.auth.security.TeamSecurity
import com.onmeet.common.dto.SecurityCheckResponse
import com.onmeet.common.dto.ErrorResponse
import com.onmeet.common.exception.BusinessException
import com.onmeet.common.exception.errorcode.AuthErrorCode
import java.security.MessageDigest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Internal Security API", description = "타 서비스 전용 권한 검증 API")
@RestController
@RequestMapping("/internal/v1/security")
class InternalSecurityController(
    private val teamSecurity: TeamSecurity,
    @Value("\${gateway.shared-secret}") private val sharedSecret: String
) {

    private fun validateSecret(secret: String?) {
        if (secret == null || !MessageDigest.isEqual(secret.toByteArray(), sharedSecret.toByteArray())) {
            // TODO: [AUTH][AuthErrorCode.INVALID_INTERNAL_SECRET] 에러메시지 검수 요청
            throw BusinessException(AuthErrorCode.INVALID_INTERNAL_SECRET)
        }
    }

    @Operation(summary = "팀장 권한 확인 (Internal)", description = "타 서비스에서 사용자가 특정 팀의 팀장인지 확인합니다. X-Internal-Secret 헤더로 인증합니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "권한 확인 성공 - 팀장 여부 반환",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = SecurityCheckResponse::class)
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - 유효하지 않은 파라미터",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 400, "message": "Invalid argument", "timestamp": 1234567890}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "401",
            description = "인증 실패 - 유효하지 않은 Internal Secret",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code": "AUTH_046", "status": 401, "message": "유효하지 않은 내부 인증 시크릿입니다", "timestamp": 1234567890}"""
                )]
            )]
        )
    ])
    @GetMapping("/teams/{teamId}/leader-check")
    fun checkLeader(
        @PathVariable teamId: Long,
        @RequestParam userId: Long,
        @RequestHeader("X-Internal-Secret") secret: String
    ): ResponseEntity<SecurityCheckResponse> {
        validateSecret(secret)
        val isLeader = teamSecurity.isLeaderOf(teamId, userId)
        return ResponseEntity.ok(SecurityCheckResponse(isLeader))
    }

    @Operation(summary = "팀 멤버 권한 확인 (Internal)", description = "타 서비스에서 사용자가 특정 팀의 멤버인지 확인합니다. X-Internal-Secret 헤더로 인증합니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "권한 확인 성공 - 팀 멤버 여부 반환",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = SecurityCheckResponse::class)
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - 유효하지 않은 파라미터",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 400, "message": "Invalid argument", "timestamp": 1234567890}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "401",
            description = "인증 실패 - 유효하지 않은 Internal Secret",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code": "AUTH_046", "status": 401, "message": "유효하지 않은 내부 인증 시크릿입니다", "timestamp": 1234567890}"""
                )]
            )]
        )
    ])
    @GetMapping("/teams/{teamId}/member-check")
    fun checkMember(
        @PathVariable teamId: Long,
        @RequestParam userId: Long,
        @RequestHeader("X-Internal-Secret") secret: String
    ): ResponseEntity<SecurityCheckResponse> {
        validateSecret(secret)
        val isMember = teamSecurity.isMemberOf(teamId, userId)
        return ResponseEntity.ok(SecurityCheckResponse(isMember))
    }

    @Operation(summary = "동일 회사 여부 확인 (Internal)", description = "타 서비스에서 사용자와 팀이 동일한 회사에 속하는지 확인합니다. X-Internal-Secret 헤더로 인증합니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "권한 확인 성공 - 동일 회사 여부 반환",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = SecurityCheckResponse::class)
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - 유효하지 않은 파라미터",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 400, "message": "Invalid argument", "timestamp": 1234567890}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "401",
            description = "인증 실패 - 유효하지 않은 Internal Secret",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code": "AUTH_046", "status": 401, "message": "유효하지 않은 내부 인증 시크릿입니다", "timestamp": 1234567890}"""
                )]
            )]
        )
    ])
    @GetMapping("/teams/{teamId}/company-check")
    fun checkCompany(
        @PathVariable teamId: Long,
        @RequestParam userId: Long,
        @RequestHeader("X-Internal-Secret") secret: String
    ): ResponseEntity<SecurityCheckResponse> {
        validateSecret(secret)
        val isSsameCompany = teamSecurity.belongsToSameCompany(teamId, userId)
        return ResponseEntity.ok(SecurityCheckResponse(isSsameCompany))
    }
}
