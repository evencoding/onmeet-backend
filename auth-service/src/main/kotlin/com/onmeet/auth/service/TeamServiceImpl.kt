package com.onmeet.auth.service

import com.onmeet.auth.config.TeamProperties
import com.onmeet.auth.dto.TeamRequest
import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.Team
import com.onmeet.auth.entity.TeamMember
import com.onmeet.auth.entity.User
import com.onmeet.auth.exception.*
import com.onmeet.auth.repository.jpa.CompanyRepository
import com.onmeet.auth.repository.jpa.TeamMemberRepository
import com.onmeet.auth.repository.jpa.TeamRepository
import com.onmeet.auth.repository.jpa.UserRepository
import com.onmeet.common.dto.NotificationRequestDto
import com.onmeet.common.exception.BusinessException
import com.onmeet.common.exception.errorcode.AuthErrorCode
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TeamServiceImpl(
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository,
    private val teamMemberRepository: TeamMemberRepository,
    private val companyRepository: CompanyRepository,
    private val teamProperties: TeamProperties,
    private val notificationEventPublisher: NotificationEventPublisher,
    private val managerTeamCreationStrategy: ManagerTeamCreationStrategy,
    private val memberTeamCreationStrategy: MemberTeamCreationStrategy
) : TeamService {

    @Transactional
    override fun createTeam(user: User, request: TeamRequest): Team {
        val company = user.company

        if (teamRepository.findByNameAndCompanyId(request.name, company.requireId()) != null) {
            throw BusinessException(AuthErrorCode.TEAM_ALREADY_EXISTS)
        }

        val strategy: TeamCreationStrategy = if (user.roles.contains(User.Role.MANAGER)) {
            managerTeamCreationStrategy
        } else {
            memberTeamCreationStrategy
        }

        return strategy.create(user, request)
    }

    @Transactional
    override fun createTeam(company: Company, name: String, description: String?, color: String?): Team =
        if (teamRepository.findByNameAndCompanyId(name, company.requireId()) != null) {
            throw BusinessException(AuthErrorCode.TEAM_ALREADY_EXISTS)
        } else {
            teamRepository.save(
                Team(
                    name = name,
                    description = description ?: teamProperties.initialDescription,
                    color = color ?: teamProperties.initialColor,
                    company = company,
                    status = Team.TeamStatus.ACTIVE
                )
            )
        }

    @Transactional
    override fun createTeam(companyId: Long, name: String, description: String?, color: String?): Team =
        (companyRepository.findByIdOrNull(companyId) ?: throw BusinessException(AuthErrorCode.COMPANY_NOT_FOUND))
            .let { createTeam(it, name, description, color) }

    @Transactional
    override fun approveTeam(teamId: Long, approver: User) {
        if (!approver.isManager()) {
            throw BusinessException(AuthErrorCode.TEAM_APPROVE_FORBIDDEN)
        }

        val team = teamRepository.findByIdOrNull(teamId)
            ?: throw BusinessException(AuthErrorCode.TEAM_NOT_FOUND)

        if (!team.belongsToCompany(approver.company.requireId())) {
            throw BusinessException(AuthErrorCode.TEAM_COMPANY_MISMATCH)
        }

        if (team.status != Team.TeamStatus.PENDING_APPROVAL) {
            throw BusinessException(AuthErrorCode.TEAM_NOT_PENDING)
        }

        team.status = Team.TeamStatus.ACTIVE

        team.leader?.let { leader ->
            teamMemberRepository.save(TeamMember(user = leader, team = team, role = TeamMember.TeamRole.LEADER))
        }

        teamRepository.save(team)

        team.leader?.let { leader ->
            notificationEventPublisher.publishNotification(
                NotificationRequestDto(
                    userId = leader.id,
                    type = "SYSTEM",
                    title = "팀 승인 완료",
                    body = "요청하신 '${team.name}' 팀 생성이 승인되었습니다.",
                    resourceType = "TEAM",
                    resourceId = team.id?.toString(),
                    actorUserId = approver.id
                )
            )
        }
    }

    @Transactional
    override fun rejectTeam(teamId: Long, approver: User, reason: String?) {
        if (!approver.isManager()) {
            throw BusinessException(AuthErrorCode.TEAM_REJECT_FORBIDDEN)
        }

        val team = teamRepository.findByIdOrNull(teamId)
            ?: throw BusinessException(AuthErrorCode.TEAM_NOT_FOUND)

        if (!team.belongsToCompany(approver.company.requireId())) {
            throw BusinessException(AuthErrorCode.TEAM_COMPANY_MISMATCH)
        }

        if (team.status != Team.TeamStatus.PENDING_APPROVAL) {
            throw BusinessException(AuthErrorCode.TEAM_NOT_PENDING)
        }

        team.status = Team.TeamStatus.REJECTED
        team.rejectionReason = reason
        teamRepository.save(team)

        team.leader?.let { leader ->
            notificationEventPublisher.publishNotification(
                NotificationRequestDto(
                    userId = leader.id,
                    type = "SYSTEM",
                    title = "팀 생성 반려",
                    body = "요청하신 '${team.name}' 팀 생성이 반려되었습니다. 사유: ${reason ?: "없음"}",
                    resourceType = "TEAM",
                    resourceId = team.id?.toString(),
                    actorUserId = approver.id
                )
            )
        }
    }

    @Transactional
    override fun assignLeader(teamId: Long, manager: User, newLeaderId: Long) {
        val team = teamRepository.findByIdOrNull(teamId)
            ?: throw BusinessException(AuthErrorCode.TEAM_NOT_FOUND)
        val newLeader = userRepository.findByIdOrNull(newLeaderId)
            ?: throw BusinessException(AuthErrorCode.USER_NOT_FOUND)

        if (!team.belongsToCompany(newLeader.company.requireId())) {
            throw BusinessException(AuthErrorCode.TEAM_DELEGATE_COMPANY_MISMATCH)
        }

        transferLeadership(
            team = team,
            newLeader = newLeader,
            oldLeaderMembership = teamMemberRepository.findLeaderByTeamId(teamId),
            notificationBody = "'${team.name}' 팀의 새로운 리더로 지정되었습니다.",
            actorUserId = manager.id
        )
    }

    @Transactional
    override fun delegateLeader(teamId: Long, currentLeader: User, newLeaderId: Long) {
        val team = teamRepository.findByIdOrNull(teamId)
            ?: throw BusinessException(AuthErrorCode.TEAM_NOT_FOUND)
        val newLeader = userRepository.findByIdOrNull(newLeaderId)
            ?: throw BusinessException(AuthErrorCode.USER_NOT_FOUND)

        if (!team.belongsToCompany(newLeader.company.requireId())) {
            throw BusinessException(AuthErrorCode.TEAM_DELEGATE_COMPANY_MISMATCH)
        }

        val currentLeaderMembership = teamMemberRepository.findByUserIdAndTeamId(currentLeader.requireId(), teamId)
            ?: throw BusinessException(AuthErrorCode.TEAM_LEADER_NOT_MEMBER)

        transferLeadership(
            team = team,
            newLeader = newLeader,
            oldLeaderMembership = currentLeaderMembership,
            notificationBody = "이전 리더에 의해 '${team.name}' 팀의 새로운 리더로 지정되었습니다.",
            actorUserId = currentLeader.id
        )
    }

    private fun transferLeadership(
        team: Team,
        newLeader: User,
        oldLeaderMembership: TeamMember?,
        notificationBody: String,
        actorUserId: Long?
    ) {
        oldLeaderMembership?.let {
            it.role = TeamMember.TeamRole.MEMBER
            teamMemberRepository.save(it)
        }

        team.leader = newLeader

        val newLeaderMembership = teamMemberRepository.findByUserIdAndTeamId(newLeader.requireId(), team.requireId())
        if (newLeaderMembership != null) {
            newLeaderMembership.role = TeamMember.TeamRole.LEADER
            teamMemberRepository.save(newLeaderMembership)
        } else {
            teamMemberRepository.save(TeamMember(user = newLeader, team = team, role = TeamMember.TeamRole.LEADER))
        }

        teamRepository.save(team)

        notificationEventPublisher.publishNotification(
            NotificationRequestDto(
                userId = newLeader.id,
                type = "SYSTEM",
                title = "팀 리더 위임",
                body = notificationBody,
                resourceType = "TEAM",
                resourceId = team.id?.toString(),
                actorUserId = actorUserId
            )
        )
    }

    @Transactional
    override fun dissolveTeam(teamId: Long, requester: User) {
        val team = teamRepository.findByIdOrNull(teamId)
            ?: throw BusinessException(AuthErrorCode.TEAM_NOT_FOUND)

        if (!team.belongsToCompany(requester.company.requireId())) {
            throw BusinessException(AuthErrorCode.TEAM_DISSOLVE_COMPANY_MISMATCH)
        }

        val teamMembers = teamMemberRepository.findByTeamId(teamId)
        teamMemberRepository.deleteAllByTeamId(teamId)
        teamRepository.delete(team)

        val memberIds = teamMembers.mapNotNull { it.user.id }
        if (memberIds.isNotEmpty()) {
            notificationEventPublisher.publishNotification(
                NotificationRequestDto(
                    userIds = memberIds,
                    type = "SYSTEM",
                    title = "팀 해체",
                    body = "'${team.name}' 팀이 해체되었습니다.",
                    actorUserId = requester.id
                )
            )
        }
    }

    @Transactional
    override fun cancelTeamRequest(teamId: Long, requester: User) {
        val team = teamRepository.findByIdOrNull(teamId)
            ?: throw BusinessException(AuthErrorCode.TEAM_NOT_FOUND)

        if (team.status != Team.TeamStatus.PENDING_APPROVAL) {
            throw BusinessException(AuthErrorCode.TEAM_CANCEL_NOT_PENDING)
        }

        if (team.leader?.id != requester.id) {
            throw BusinessException(AuthErrorCode.TEAM_CANCEL_FORBIDDEN)
        }

        teamRepository.delete(team)
    }

    override fun teamExists(teamId: Long): Boolean = teamRepository.existsById(teamId)

    override fun isTeamMember(teamId: Long, userId: Long): Boolean =
        teamMemberRepository.findByUserIdAndTeamId(userId, teamId) != null

    override fun getTeamMemberIds(teamId: Long): List<Long> =
        teamMemberRepository.findByTeamId(teamId).mapNotNull { it.user.id }
}
