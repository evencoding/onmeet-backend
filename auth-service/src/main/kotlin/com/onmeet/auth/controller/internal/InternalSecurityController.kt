package com.onmeet.auth.controller.internal

import com.onmeet.auth.security.TeamSecurity
import com.onmeet.common.dto.SecurityCheckResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
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
        if (secret != sharedSecret) {
            throw RuntimeException("Invalid internal secret")
        }
    }

    @Operation(summary = "팀장 권한 확인 (Internal)")
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

    @Operation(summary = "팀 멤버 권한 확인 (Internal)")
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

    @Operation(summary = "동일 회사 여부 확인 (Internal)")
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
