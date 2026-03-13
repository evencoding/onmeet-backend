package com.onmeet.auth.service

import com.onmeet.auth.config.TeamProperties
import com.onmeet.auth.dto.TeamRequest
import com.onmeet.auth.entity.Team
import com.onmeet.auth.entity.TeamMember
import com.onmeet.auth.entity.User
import com.onmeet.auth.entity.Company
import com.onmeet.auth.exception.*
import com.onmeet.common.exception.BusinessException
import com.onmeet.common.exception.errorcode.AuthErrorCode
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
            // TODO: [AUTH][AuthErrorCode.TEAM_ALREADY_EXISTS] 에러메시지 검수 요청
            throw BusinessException(AuthErrorCode.TEAM_ALREADY_EXISTS)
        }

        val isManager = user.roles.contains(User.Role.MANAGER)
        val initialStatus = if (isManager) Team.TeamStatus.ACTIVE else Team.TeamStatus.PENDING_APPROVAL

        // MANAGER: 팀원과 팀장을 지정하여 생성
        if (isManager) {
            if (request.memberIds.isNullOrEmpty() || request.leaderId == null) {
                // TODO: [AUTH][AuthErrorCode.TEAM_MEMBERS_REQUIRED] 에러메시지 검수 요청
                throw BusinessException(AuthErrorCode.TEAM_MEMBERS_REQUIRED)
            }

            if (!request.memberIds.contains(request.leaderId)) {
                // TODO: [AUTH][AuthErrorCode.LEADER_NOT_IN_MEMBERS] 에러메시지 검수 요청
                throw BusinessException(AuthErrorCode.LEADER_NOT_IN_MEMBERS)
            }

            val members = userRepository.findAllById(request.memberIds)
            if (members.size != request.memberIds.size) {
                // TODO: [AUTH][AuthErrorCode.TEAM_MEMBER_NOT_FOUND] 에러메시지 검수 요청
                throw BusinessException(AuthErrorCode.TEAM_MEMBER_NOT_FOUND)
            }

            members.forEach { member ->
                if (member.company.requireId() != company.requireId()) {
                    // TODO: [AUTH][AuthErrorCode.TEAM_MEMBER_COMPANY_MISMATCH] 에러메시지 검수 요청
                    throw BusinessException(AuthErrorCode.TEAM_MEMBER_COMPANY_MISMATCH)
                }
            }

            val leader = members.find { it.id == request.leaderId }
                // TODO: [AUTH][AuthErrorCode.TEAM_LEADER_NOT_FOUND] 에러메시지 검수 요청
                ?: throw BusinessException(AuthErrorCode.TEAM_LEADER_NOT_FOUND)

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

            // 팀원들에게 팀 초대 알림 전송 (벌크)
            val targetUserIds = members.filter { it.id != request.leaderId }.mapNotNull { it.id }
            if (targetUserIds.isNotEmpty()) {
                notificationEventPublisher.publishNotification(
                    com.onmeet.common.dto.NotificationRequestDto(
                        userIds = targetUserIds,
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
        
        // 같은 회사 내 매니저 권한을 가진 사람들에게 팀 생성 요청 알림 전송 (벌크)
        val managerIds = userRepository.findByCompanyIdAndRole(company.requireId(), User.Role.MANAGER).mapNotNull { it.id }
        if (managerIds.isNotEmpty()) {
            notificationEventPublisher.publishNotification(
                com.onmeet.common.dto.NotificationRequestDto(
                    userIds = managerIds,
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
            // TODO: [AUTH][AuthErrorCode.TEAM_ALREADY_EXISTS] 에러메시지 검수 요청
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
        companyRepository.findById(companyId)
            // TODO: [AUTH][AuthErrorCode.COMPANY_NOT_FOUND] 에러메시지 검수 요청
            .orElseThrow { BusinessException(AuthErrorCode.COMPANY_NOT_FOUND) }
            .let { createTeam(it, name, description, color) }

    @Transactional
    override fun approveTeam(teamId: Long, approver: User): Unit {
        // Service layer permission validation (defense in depth with Controller's @PreAuthorize)
        if (!approver.isManager()) {
            // TODO: [AUTH][AuthErrorCode.TEAM_APPROVE_FORBIDDEN] 에러메시지 검수 요청
            throw BusinessException(AuthErrorCode.TEAM_APPROVE_FORBIDDEN)
        }

        val team = teamRepository.findById(teamId)
            // TODO: [AUTH][AuthErrorCode.TEAM_NOT_FOUND] 에러메시지 검수 요청
            .orElseThrow { BusinessException(AuthErrorCode.TEAM_NOT_FOUND) }

        // Verify the manager belongs to the same company
        if (!team.belongsToCompany(approver.company.requireId())) {
            // TODO: [AUTH][AuthErrorCode.TEAM_COMPANY_MISMATCH] 에러메시지 검수 요청
            throw BusinessException(AuthErrorCode.TEAM_COMPANY_MISMATCH)
        }

        // Only pending teams can be approved
        if (team.status != Team.TeamStatus.PENDING_APPROVAL) {
            // TODO: [AUTH][AuthErrorCode.TEAM_NOT_PENDING] 에러메시지 검수 요청
            throw BusinessException(AuthErrorCode.TEAM_NOT_PENDING)
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
            // TODO: [AUTH][AuthErrorCode.TEAM_REJECT_FORBIDDEN] 에러메시지 검수 요청
            throw BusinessException(AuthErrorCode.TEAM_REJECT_FORBIDDEN)
        }

        val team = teamRepository.findById(teamId)
            // TODO: [AUTH][AuthErrorCode.TEAM_NOT_FOUND] 에러메시지 검수 요청
            .orElseThrow { BusinessException(AuthErrorCode.TEAM_NOT_FOUND) }

        // Verify the manager belongs to the same company
        if (!team.belongsToCompany(approver.company.requireId())) {
            // TODO: [AUTH][AuthErrorCode.TEAM_COMPANY_MISMATCH] 에러메시지 검수 요청
            throw BusinessException(AuthErrorCode.TEAM_COMPANY_MISMATCH)
        }

        // Only pending teams can be rejected
        if (team.status != Team.TeamStatus.PENDING_APPROVAL) {
            // TODO: [AUTH][AuthErrorCode.TEAM_NOT_PENDING] 에러메시지 검수 요청
            throw BusinessException(AuthErrorCode.TEAM_NOT_PENDING)
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
            // TODO: [AUTH][AuthErrorCode.TEAM_NOT_FOUND] 에러메시지 검수 요청
            .orElseThrow { BusinessException(AuthErrorCode.TEAM_NOT_FOUND) }

        val newLeader = userRepository.findById(newLeaderId)
            // TODO: [AUTH][AuthErrorCode.USER_NOT_FOUND] 에러메시지 검수 요청
            .orElseThrow { BusinessException(AuthErrorCode.USER_NOT_FOUND) }

        if (!team.belongsToCompany(newLeader.company.requireId())) {
            // TODO: [AUTH][AuthErrorCode.TEAM_DELEGATE_COMPANY_MISMATCH] 에러메시지 검수 요청
            throw BusinessException(AuthErrorCode.TEAM_DELEGATE_COMPANY_MISMATCH)
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
            // TODO: [AUTH][AuthErrorCode.TEAM_NOT_FOUND] 에러메시지 검수 요청
            .orElseThrow { BusinessException(AuthErrorCode.TEAM_NOT_FOUND) }

        val newLeader = userRepository.findById(newLeaderId)
            // TODO: [AUTH][AuthErrorCode.USER_NOT_FOUND] 에러메시지 검수 요청
            .orElseThrow { BusinessException(AuthErrorCode.USER_NOT_FOUND) }

        if (!team.belongsToCompany(newLeader.company.requireId())) {
            // TODO: [AUTH][AuthErrorCode.TEAM_DELEGATE_COMPANY_MISMATCH] 에러메시지 검수 요청
            throw BusinessException(AuthErrorCode.TEAM_DELEGATE_COMPANY_MISMATCH)
        }

        // 현재 리더의 역할을 MEMBER로 변경
        val currentLeaderMembership = teamMemberRepository.findByUserIdAndTeamId(currentLeader.requireId(), teamId)
            // TODO: [AUTH][AuthErrorCode.TEAM_LEADER_NOT_MEMBER] 에러메시지 검수 요청
            ?: throw BusinessException(AuthErrorCode.TEAM_LEADER_NOT_MEMBER)
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
            // TODO: [AUTH][AuthErrorCode.TEAM_NOT_FOUND] 에러메시지 검수 요청
            .orElseThrow { BusinessException(AuthErrorCode.TEAM_NOT_FOUND) }

        // Verify the requester belongs to the same company
        if (!team.belongsToCompany(requester.company.requireId())) {
            // TODO: [AUTH][AuthErrorCode.TEAM_DISSOLVE_COMPANY_MISMATCH] 에러메시지 검수 요청
            throw BusinessException(AuthErrorCode.TEAM_DISSOLVE_COMPANY_MISMATCH)
        }

        // 팀 해체 알림을 위해 팀원 목록 미리 조회
        val teamMembers = teamMemberRepository.findByTeamId(teamId)

        // Explicitly delete all TeamMember associations before deleting the team
        // (defense in depth even though cascade should handle it)
        teamMemberRepository.deleteAllByTeamId(teamId)

        teamRepository.delete(team)
        // 팀 해체 알림 전송 (벌크)
        val memberIds = teamMembers.mapNotNull { it.user.id }
        if (memberIds.isNotEmpty()) {
            notificationEventPublisher.publishNotification(
                com.onmeet.common.dto.NotificationRequestDto(
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
    override fun cancelTeamRequest(teamId: Long, requester: User): Unit {
        val team = teamRepository.findById(teamId)
            // TODO: [AUTH][AuthErrorCode.TEAM_NOT_FOUND] 에러메시지 검수 요청
            .orElseThrow { BusinessException(AuthErrorCode.TEAM_NOT_FOUND) }

        if (team.status != Team.TeamStatus.PENDING_APPROVAL) {
            // TODO: [AUTH][AuthErrorCode.TEAM_CANCEL_NOT_PENDING] 에러메시지 검수 요청
            throw BusinessException(AuthErrorCode.TEAM_CANCEL_NOT_PENDING)
        }

        if (team.leader?.id != requester.id) {
            // TODO: [AUTH][AuthErrorCode.TEAM_CANCEL_FORBIDDEN] 에러메시지 검수 요청
            throw BusinessException(AuthErrorCode.TEAM_CANCEL_FORBIDDEN)
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
