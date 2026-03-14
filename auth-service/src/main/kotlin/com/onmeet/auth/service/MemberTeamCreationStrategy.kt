package com.onmeet.auth.service

import com.onmeet.auth.dto.TeamRequest
import com.onmeet.auth.entity.Team
import com.onmeet.auth.entity.User
import com.onmeet.auth.repository.jpa.TeamRepository
import com.onmeet.auth.repository.jpa.UserRepository
import com.onmeet.common.dto.NotificationRequestDto
import org.springframework.stereotype.Component

@Component
class MemberTeamCreationStrategy(
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository,
    private val notificationEventPublisher: NotificationEventPublisher
) : TeamCreationStrategy {

    override fun create(user: User, request: TeamRequest): Team {
        val company = user.company

        val team = teamRepository.save(
            Team(
                name = request.name,
                description = request.description,
                color = request.color,
                company = company,
                leader = user,
                status = Team.TeamStatus.PENDING_APPROVAL
            )
        )

        val managerIds = userRepository.findByCompanyIdAndRole(company.requireId(), User.Role.MANAGER).mapNotNull { it.id }
        if (managerIds.isNotEmpty()) {
            notificationEventPublisher.publishNotification(
                NotificationRequestDto(
                    userIds = managerIds,
                    type = "SYSTEM",
                    title = "팀 생성 요청",
                    body = "${user.name}님이 '${team.name}' 팀 생성을 요청했습니다.",
                    resourceType = "TEAM",
                    resourceId = team.id?.toString(),
                    actorUserId = user.id
                )
            )
        }

        return team
    }
}
