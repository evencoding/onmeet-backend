package com.onmeet.auth.service

import com.onmeet.auth.dto.TeamRequest
import com.onmeet.auth.entity.Team
import com.onmeet.auth.entity.TeamMember
import com.onmeet.auth.entity.User
import com.onmeet.auth.repository.jpa.TeamMemberRepository
import com.onmeet.auth.repository.jpa.TeamRepository
import com.onmeet.auth.repository.jpa.UserRepository
import com.onmeet.common.dto.NotificationRequestDto
import com.onmeet.common.exception.BusinessException
import com.onmeet.common.exception.errorcode.AuthErrorCode
import org.springframework.stereotype.Component

@Component
class ManagerTeamCreationStrategy(
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
    private val teamMemberRepository: TeamMemberRepository,
    private val notificationEventPublisher: NotificationEventPublisher
) : TeamCreationStrategy {

    override fun create(user: User, request: TeamRequest): Team {
        val company = user.company

        if (request.memberIds.isNullOrEmpty() || request.leaderId == null) {
            throw BusinessException(AuthErrorCode.TEAM_MEMBERS_REQUIRED)
        }

        if (!request.memberIds.contains(request.leaderId)) {
            throw BusinessException(AuthErrorCode.LEADER_NOT_IN_MEMBERS)
        }

        val members = userRepository.findAllById(request.memberIds)
        if (members.size != request.memberIds.size) {
            throw BusinessException(AuthErrorCode.TEAM_MEMBER_NOT_FOUND)
        }

        members.forEach { member ->
            if (member.company.requireId() != company.requireId()) {
                throw BusinessException(AuthErrorCode.TEAM_MEMBER_COMPANY_MISMATCH)
            }
        }

        // leader is guaranteed to be in members: leaderId was validated to be in memberIds (line 30)
        // and all memberIds were confirmed to exist in DB (line 35)
        val leader = members.first { it.id == request.leaderId }

        val team = teamRepository.save(
            Team(
                name = request.name,
                description = request.description,
                color = request.color,
                company = company,
                leader = leader,
                status = Team.TeamStatus.ACTIVE
            )
        )

        val teamMembers = members.map { member ->
            val role = if (member.id == request.leaderId) TeamMember.TeamRole.LEADER else TeamMember.TeamRole.MEMBER
            TeamMember(user = member, team = team, role = role)
        }
        teamMemberRepository.saveAll(teamMembers)

        val targetUserIds = members.filter { it.id != request.leaderId }.mapNotNull { it.id }
        if (targetUserIds.isNotEmpty()) {
            notificationEventPublisher.publishNotification(
                NotificationRequestDto(
                    userIds = targetUserIds,
                    type = "TEAM_MEMBER_ADDED",
                    title = team.name,
                    body = "새로운 팀에 초대되었습니다.",
                    resourceType = "TEAM",
                    resourceId = team.id?.toString(),
                    actorUserId = user.id
                )
            )
        }

        return team
    }
}
