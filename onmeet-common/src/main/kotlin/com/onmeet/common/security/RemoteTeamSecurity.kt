package com.onmeet.common.security

import com.onmeet.common.client.InternalSecurityClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component

// NOTE: Consuming services must enable caching via @EnableCaching and configure a cache provider
// (e.g., Caffeine with a 5-minute TTL) in their own CacheConfig.
// Recommended config: maximumSize=1000, expireAfterWrite=5m
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

    @Cacheable(value = ["teamLeadership"], key = "#principal?.toString() + '-' + #teamId")
    override fun isLeaderOf(teamId: Long, principal: Any?): Boolean {
        val userId = extractUserId(principal) ?: return false
        return securityClient.checkLeader(teamId, userId, sharedSecret).authorized
    }

    @Cacheable(value = ["teamMembership"], key = "#principal?.toString() + '-' + #teamId")
    override fun isMemberOf(teamId: Long, principal: Any?): Boolean {
        val userId = extractUserId(principal) ?: return false
        return securityClient.checkMember(teamId, userId, sharedSecret).authorized
    }

    @Cacheable(value = ["teamCompany"], key = "#principal?.toString() + '-' + #teamId")
    override fun belongsToSameCompany(teamId: Long, principal: Any?): Boolean {
        val userId = extractUserId(principal) ?: return false
        return securityClient.checkCompany(teamId, userId, sharedSecret).authorized
    }
}
