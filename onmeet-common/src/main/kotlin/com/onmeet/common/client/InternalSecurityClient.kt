package com.onmeet.common.client

import com.onmeet.common.dto.SecurityCheckResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(
    name = "auth-service",
    url = "\${onmeet.auth.internal-url:http://auth-service:8081}",
    path = "/auth/internal/v1/security"
)
interface InternalSecurityClient {

    @GetMapping("/teams/{teamId}/leader-check")
    fun checkLeader(
        @PathVariable teamId: Long,
        @RequestParam userId: Long,
        @RequestHeader("X-Internal-Secret") secret: String
    ): SecurityCheckResponse

    @GetMapping("/teams/{teamId}/member-check")
    fun checkMember(
        @PathVariable teamId: Long,
        @RequestParam userId: Long,
        @RequestHeader("X-Internal-Secret") secret: String
    ): SecurityCheckResponse

    @GetMapping("/teams/{teamId}/company-check")
    fun checkCompany(
        @PathVariable teamId: Long,
        @RequestParam userId: Long,
        @RequestHeader("X-Internal-Secret") secret: String
    ): SecurityCheckResponse
}
