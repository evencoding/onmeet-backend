package com.onmeet.common.security

import com.onmeet.common.client.InternalSecurityClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component("teamSecurity")
class RemoteTeamSecurity(
    private val securityClient: InternalSecurityClient,
    @Value("\${gateway.shared-secret}") private val sharedSecret: String
) : TeamSecurity {

    private fun extractUserId(principal: Any?): Long? {
        return when (principal) {
            is String -> principal.toLongOrNull()
            is Number -> principal.toLong()
            else -> null
        }
    }

    override fun isLeaderOf(teamId: Long, principal: Any?): Boolean {
        val userId = extractUserId(principal) ?: return false
        return securityClient.checkLeader(teamId, userId, sharedSecret).authorized
    }

    override fun isMemberOf(teamId: Long, principal: Any?): Boolean {
        val userId = extractUserId(principal) ?: return false
        return securityClient.checkMember(teamId, userId, sharedSecret).authorized
    }

    override fun belongsToSameCompany(teamId: Long, principal: Any?): Boolean {
        val userId = extractUserId(principal) ?: return false
        return securityClient.checkCompany(teamId, userId, sharedSecret).authorized
    }
}
