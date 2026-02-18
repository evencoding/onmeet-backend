package com.onmeet.auth.security

import com.onmeet.auth.entity.TeamMember
import com.onmeet.auth.entity.User
import com.onmeet.auth.repository.jpa.TeamMemberRepository
import com.onmeet.auth.repository.jpa.TeamRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

import com.onmeet.auth.repository.jpa.UserRepository
import com.onmeet.common.security.TeamSecurity as SharedTeamSecurity
import org.springframework.context.annotation.Primary

@Primary
@Component("teamSecurity")
class TeamSecurity(
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository,
    private val teamMemberRepository: TeamMemberRepository
) : SharedTeamSecurity {

    override fun isLeaderOf(teamId: Long, principal: Any?): Boolean {
        if (principal is Long) {
            val user = userRepository.findById(principal).orElse(null) ?: return false
            return isLeaderOf(teamId, user)
        }
        return isLeaderOfInternal(teamId, principal)
    }

    override fun isMemberOf(teamId: Long, principal: Any?): Boolean {
        if (principal is Long) {
            val user = userRepository.findById(principal).orElse(null) ?: return false
            return isMemberOfInternal(teamId, user)
        }
        return isMemberOfInternal(teamId, principal)
    }

    override fun belongsToSameCompany(teamId: Long, principal: Any?): Boolean {
        if (principal is Long) {
            val user = userRepository.findById(principal).orElse(null) ?: return false
            return belongsToSameCompanyInternal(teamId, user)
        }
        return belongsToSameCompanyInternal(teamId, principal)
    }

    @Transactional(readOnly = true)
    private fun isLeaderOfInternal(teamId: Long, principal: Any?): Boolean {
        if (principal !is User) return false

        val team = teamRepository.findById(teamId).orElse(null) ?: return false

        // 1. 같은 회사 소속인지 확인
        if (!team.belongsToCompany(principal.company.requireId())) return false

        // 2. TeamMember를 통해 해당 팀의 리더인지 확인
        return teamMemberRepository.existsByUserIdAndTeamIdAndRole(
            principal.requireId(),
            teamId,
            TeamMember.TeamRole.LEADER
        )
    }

    @Transactional(readOnly = true)
    private fun isMemberOfInternal(teamId: Long, principal: Any?): Boolean {
        if (principal !is User) return false

        val team = teamRepository.findById(teamId).orElse(null) ?: return false

        // 같은 회사 소속인지 확인
        if (!team.belongsToCompany(principal.company.requireId())) return false

        // TeamMember를 통해 팀 멤버인지 확인 (LEADER 또는 MEMBER 역할)
        return teamMemberRepository.findByUserIdAndTeamId(principal.requireId(), teamId) != null
    }
    
    @Transactional(readOnly = true)
    private fun belongsToSameCompanyInternal(teamId: Long, principal: Any?): Boolean {
        if (principal !is User) return false
        val team = teamRepository.findById(teamId).orElse(null) ?: return false
        return team.belongsToCompany(principal.company.requireId())
    }
}
