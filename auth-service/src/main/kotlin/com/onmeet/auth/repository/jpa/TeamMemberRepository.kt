package com.onmeet.auth.repository.jpa

import com.onmeet.auth.entity.TeamMember
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.data.jpa.repository.Query

@Repository
interface TeamMemberRepository : JpaRepository<TeamMember, Long> {

    fun findByUserIdAndTeamId(userId: Long, teamId: Long): TeamMember?

    fun findByTeamId(teamId: Long): List<TeamMember>

    fun findByUserId(userId: Long): List<TeamMember>

    @Query("SELECT tm FROM TeamMember tm WHERE tm.team.id = :teamId AND tm.role = 'LEADER'")
    fun findLeaderByTeamId(teamId: Long): TeamMember?

    fun existsByUserIdAndTeamIdAndRole(userId: Long, teamId: Long, role: TeamMember.TeamRole): Boolean

    fun deleteByUserIdAndTeamId(userId: Long, teamId: Long)

    fun deleteAllByTeamId(teamId: Long)
}
