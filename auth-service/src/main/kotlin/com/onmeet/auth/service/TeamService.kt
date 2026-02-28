package com.onmeet.auth.service

import com.onmeet.auth.dto.TeamRequest
import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.Team
import com.onmeet.auth.entity.User

interface TeamService {
    fun createTeam(user: User, request: TeamRequest): Team
    fun createTeam(company: Company, name: String, description: String? = null, color: String? = null): Team
    fun createTeam(companyId: Long, name: String, description: String? = null, color: String? = null): Team
    
    fun approveTeam(teamId: Long, approver: User): Unit
    fun rejectTeam(teamId: Long, approver: User, reason: String? = null): Unit
    fun assignLeader(teamId: Long, manager: User, newLeaderId: Long)
    fun delegateLeader(teamId: Long, currentLeader: User, newLeaderId: Long)
    fun dissolveTeam(teamId: Long, requester: User): Unit
    fun cancelTeamRequest(teamId: Long, requester: User): Unit
}
