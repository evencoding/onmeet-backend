package com.onmeet.auth.service

import com.onmeet.auth.config.TeamProperties
import com.onmeet.auth.dto.TeamRequest
import com.onmeet.auth.entity.Team
import com.onmeet.auth.entity.TeamMember
import com.onmeet.auth.entity.User
import com.onmeet.auth.entity.Company
import com.onmeet.common.exception.InsufficientPermissionException
import com.onmeet.auth.exception.*
import com.onmeet.auth.repository.jpa.TeamMemberRepository
import com.onmeet.auth.repository.jpa.TeamRepository
import com.onmeet.auth.repository.jpa.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TeamServiceImpl(
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository,
    private val teamMemberRepository: TeamMemberRepository,
    private val companyRepository: com.onmeet.auth.repository.jpa.CompanyRepository,
    private val teamProperties: TeamProperties,
    private val notificationEventPublisher: NotificationEventPublisher
) : TeamService {

    @Transactional
    override fun createTeam(user: User, request: TeamRequest): Team {
        val company = user.company

        if (teamRepository.findByNameAndCompanyId(request.name, company.requireId()) != null) {
            throw TeamAlreadyExistsException("Team already exists in this company: ${request.name}")
        }

        val isManager = user.roles.contains(User.Role.MANAGER)
        val initialStatus = if (isManager) Team.TeamStatus.ACTIVE else Team.TeamStatus.PENDING_APPROVAL

        // MANAGER: 팀원과 팀장을 지정하여 생성
        if (isManager) {
            if (request.memberIds.isNullOrEmpty() || request.leaderId == null) {
                throw IllegalArgumentException("Manager must specify member IDs and leader ID")
            }

            if (!request.memberIds.contains(request.leaderId)) {
                throw IllegalArgumentException("Leader must be one of the team members")
            }

            val members = userRepository.findAllById(request.memberIds)
            if (members.size != request.memberIds.size) {
                throw UserNotFoundException("Some members not found")
            }

            members.forEach { member ->
                if (member.company.requireId() != company.requireId()) {
                    throw CompanyMismatchException("All members must belong to the same company")
                }
            }

            val leader = members.find { it.id == request.leaderId }
                ?: throw UserNotFoundException("Leader not found in member list")

            val team = Team(
                name = request.name,
                description = request.description,
                color = request.color,
                company = company,
                leader = leader,
                status = initialStatus
            )

            val savedTeam = teamRepository.save(team)

            // TeamMember 엔티티를 통해 팀원 추가 및 역할 지정
            members.forEach { member ->
                val role = if (member.id == request.leaderId) {
                    TeamMember.TeamRole.LEADER
                } else {
                    TeamMember.TeamRole.MEMBER
                }

                val teamMember = TeamMember(
                    user = member,
                    team = savedTeam,
                    role = role
                )
                teamMemberRepository.save(teamMember)
            }

            // 팀원들에게 팀 초대 알림 전송 (매니저가 팀 생성 시)
            members.filter { it.id != request.leaderId }.forEach { member ->
                notificationEventPublisher.publishNotification(
                    com.onmeet.common.dto.NotificationRequestDto(
                        userId = member.id,
                        type = "TEAM_MEMBER_ADDED",
                        title = savedTeam.name,
                        body = "새로운 팀에 초대되었습니다.",
                        resourceType = "TEAM",
                        resourceId = savedTeam.id?.toString(),
                        actorUserId = user.id
                    )
                )
            }
            return savedTeam
        }

        // 일반 유저: PENDING_APPROVAL 상태로 생성하고 신청자가 승인 시 팀장이 됨
        val team = Team(
            name = request.name,
            description = request.description,
            color = request.color,
            company = company,
            leader = user,
            status = initialStatus
        )

        val savedTeam = teamRepository.save(team)
        
        // 같은 회사 내 매니저 권한을 가진 사람들에게 팀 생성 요청 알림 전송
        val managers = userRepository.findByCompanyIdAndRole(company.requireId(), User.Role.MANAGER)
        managers.forEach { manager ->
            notificationEventPublisher.publishNotification(
                com.onmeet.common.dto.NotificationRequestDto(
                    userId = manager.id,
                    type = "SYSTEM",
                    title = "팀 생성 요청",
                    body = "${user.name}님이 '${savedTeam.name}' 팀 생성을 요청했습니다.",
                    resourceType = "TEAM",
                    resourceId = savedTeam.id?.toString(),
                    actorUserId = user.id
                )
            )
        }
        return savedTeam
    }

    @Transactional
    override fun createTeam(company: Company, name: String, description: String?, color: String?): Team =
        if (teamRepository.findByNameAndCompanyId(name, company.requireId()) != null) {
            throw TeamAlreadyExistsException("Team already exists in this company: $name")
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
        companyRepository.findById(companyId)
            .orElseThrow { CompanyNotFoundException("Company not found: $companyId") }
            .let { createTeam(it, name, description, color) }

    @Transactional
    override fun approveTeam(teamId: Long, approver: User): Unit {
        // Service layer permission validation (defense in depth with Controller's @PreAuthorize)
        if (!approver.isManager()) {
            throw InsufficientPermissionException("Only managers can approve teams")
        }

        val team = teamRepository.findById(teamId)
            .orElseThrow { TeamNotFoundException("Team not found: $teamId") }

        // Verify the manager belongs to the same company
        if (!team.belongsToCompany(approver.company.requireId())) {
            throw CompanyMismatchException("Manager can only approve teams in their own company")
        }

        // Only pending teams can be approved
        if (team.status != Team.TeamStatus.PENDING_APPROVAL) {
            throw IllegalStateException("Only pending teams can be approved (current: ${team.status})")
        }

        team.status = Team.TeamStatus.ACTIVE

        // PENDING_APPROVAL 팀의 리더(신청자)를 팀에 추가하고 LEADER 역할 부여
        team.leader?.let { leader ->
            val teamMember = TeamMember(
                user = leader,
                team = team,
                role = TeamMember.TeamRole.LEADER
            )
            teamMemberRepository.save(teamMember)
        }

        val approvedTeam = teamRepository.save(team)
        // 팀 생성 요청자(팀장)에게 승인 알림 전송
        team.leader?.let { leader ->
            notificationEventPublisher.publishNotification(
                com.onmeet.common.dto.NotificationRequestDto(
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
    override fun rejectTeam(teamId: Long, approver: User, reason: String?): Unit {
        // Service layer permission validation (defense in depth with Controller's @PreAuthorize)
        if (!approver.isManager()) {
            throw InsufficientPermissionException("Only managers can reject teams")
        }

        val team = teamRepository.findById(teamId)
            .orElseThrow { TeamNotFoundException("Team not found: $teamId") }

        // Verify the manager belongs to the same company
        if (!team.belongsToCompany(approver.company.requireId())) {
            throw CompanyMismatchException("Manager can only reject teams in their own company")
        }

        // Only pending teams can be rejected
        if (team.status != Team.TeamStatus.PENDING_APPROVAL) {
            throw IllegalStateException("Only pending teams can be rejected (current: ${team.status})")
        }

        team.status = Team.TeamStatus.REJECTED
        team.rejectionReason = reason

        val rejectedTeam = teamRepository.save(team)
        // 팀 생성 요청자에게 반려 알림 전송
        team.leader?.let { leader ->
            notificationEventPublisher.publishNotification(
                com.onmeet.common.dto.NotificationRequestDto(
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
        val team = teamRepository.findById(teamId)
            .orElseThrow { TeamNotFoundException("Team not found: $teamId") }

        val newLeader = userRepository.findById(newLeaderId)
            .orElseThrow { UserNotFoundException("User not found: $newLeaderId") }

        if (!team.belongsToCompany(newLeader.company.requireId())) {
            throw CompanyMismatchException("User must belong to the same company")
        }

        // 기존 리더의 역할을 MEMBER로 변경
        teamMemberRepository.findLeaderByTeamId(teamId)?.let { oldLeaderMembership ->
            oldLeaderMembership.role = TeamMember.TeamRole.MEMBER
            teamMemberRepository.save(oldLeaderMembership)
        }

        // 새 리더 설정
        team.leader = newLeader

        // 새 리더의 TeamMember 역할을 LEADER로 변경 (이미 팀원인 경우) 또는 추가
        val newLeaderMembership = teamMemberRepository.findByUserIdAndTeamId(newLeaderId, teamId)
        if (newLeaderMembership != null) {
            newLeaderMembership.role = TeamMember.TeamRole.LEADER
            teamMemberRepository.save(newLeaderMembership)
        } else {
            val teamMember = TeamMember(
                user = newLeader,
                team = team,
                role = TeamMember.TeamRole.LEADER
            )
            teamMemberRepository.save(teamMember)
        }

        val updatedTeam = teamRepository.save(team)
        // 팀장 위임 알림 전송
        notificationEventPublisher.publishNotification(
            com.onmeet.common.dto.NotificationRequestDto(
                userId = newLeader.id,
                type = "SYSTEM",
                title = "팀 리더 위임",
                body = "'${team.name}' 팀의 새로운 리더로 지정되었습니다.",
                resourceType = "TEAM",
                resourceId = team.id?.toString(),
                actorUserId = manager.id
            )
        )
    }

    @Transactional
    override fun delegateLeader(teamId: Long, currentLeader: User, newLeaderId: Long) {
        val team = teamRepository.findById(teamId)
            .orElseThrow { TeamNotFoundException("Team not found: $teamId") }

        val newLeader = userRepository.findById(newLeaderId)
            .orElseThrow { UserNotFoundException("User not found: $newLeaderId") }

        if (!team.belongsToCompany(newLeader.company.requireId())) {
            throw CompanyMismatchException("User must belong to the same company")
        }

        // 현재 리더의 역할을 MEMBER로 변경
        val currentLeaderMembership = teamMemberRepository.findByUserIdAndTeamId(currentLeader.requireId(), teamId)
            ?: throw IllegalStateException("Current leader is not a team member")
        currentLeaderMembership.role = TeamMember.TeamRole.MEMBER
        teamMemberRepository.save(currentLeaderMembership)

        // 새 리더 설정
        team.leader = newLeader

        // 새 리더의 TeamMember 역할을 LEADER로 변경 (이미 팀원인 경우) 또는 추가
        val newLeaderMembership = teamMemberRepository.findByUserIdAndTeamId(newLeaderId, teamId)
        if (newLeaderMembership != null) {
            newLeaderMembership.role = TeamMember.TeamRole.LEADER
            teamMemberRepository.save(newLeaderMembership)
        } else {
            val teamMember = TeamMember(
                user = newLeader,
                team = team,
                role = TeamMember.TeamRole.LEADER
            )
            teamMemberRepository.save(teamMember)
        }

        val updatedTeam = teamRepository.save(team)
        // 팀장 위임 알림 전송
        notificationEventPublisher.publishNotification(
            com.onmeet.common.dto.NotificationRequestDto(
                userId = newLeader.id,
                type = "SYSTEM",
                title = "팀 리더 위임",
                body = "이전 리더에 의해 '${team.name}' 팀의 새로운 리더로 지정되었습니다.",
                resourceType = "TEAM",
                resourceId = team.id?.toString(),
                actorUserId = currentLeader.id
            )
        )
    }

    @Transactional
    override fun dissolveTeam(teamId: Long, requester: User): Unit {
        val team = teamRepository.findById(teamId)
            .orElseThrow { TeamNotFoundException("Team not found: $teamId") }

        // Verify the requester belongs to the same company
        if (!team.belongsToCompany(requester.company.requireId())) {
            throw CompanyMismatchException("Cannot dissolve teams in another company")
        }

        // 팀 해체 알림을 위해 팀원 목록 미리 조회
        val teamMembers = teamMemberRepository.findAllByTeamId(teamId)

        // Explicitly delete all TeamMember associations before deleting the team
        // (defense in depth even though cascade should handle it)
        teamMemberRepository.deleteAllByTeamId(teamId)

        teamRepository.delete(team)
        // 팀 해체 알림 전송
        teamMembers.forEach { member ->
            notificationEventPublisher.publishNotification(
                com.onmeet.common.dto.NotificationRequestDto(
                    userId = member.user.id,
                    type = "SYSTEM",
                    title = "팀 해체",
                    body = "'${team.name}' 팀이 해체되었습니다.",
                    actorUserId = requester.id
                )
            )
        }
    }

    @Transactional
    override fun cancelTeamRequest(teamId: Long, requester: User): Unit {
        val team = teamRepository.findById(teamId)
            .orElseThrow { TeamNotFoundException("Team not found: $teamId") }

        if (team.status != Team.TeamStatus.PENDING_APPROVAL) {
            throw IllegalStateException("Only pending team requests can be cancelled")
        }

        if (team.leader?.id != requester.id) {
            throw InsufficientPermissionException("Only the requester can cancel the team creation request")
        }

        teamRepository.delete(team)
    }

    // Internal API - No permission check
    override fun teamExists(teamId: Long): Boolean {
        return teamRepository.existsById(teamId)
    }

    // Internal API - No permission check
    override fun isTeamMember(teamId: Long, userId: Long): Boolean {
        return teamMemberRepository.findByUserIdAndTeamId(userId, teamId) != null
    }
}
