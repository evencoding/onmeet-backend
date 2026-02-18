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
    private val teamProperties: TeamProperties
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

        return teamRepository.save(team)
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
        val team = teamRepository.findById(teamId)
            .orElseThrow { TeamNotFoundException("Team not found: $teamId") }

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

        teamRepository.save(team)
    }

    @Transactional
    override fun rejectTeam(teamId: Long, approver: User): Unit {
        teamRepository.findById(teamId)
            .orElseThrow { TeamNotFoundException("Team not found: $teamId") }
            .let { teamRepository.delete(it) }
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

        teamRepository.save(team)
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

        teamRepository.save(team)
    }

    @Transactional
    override fun dissolveTeam(teamId: Long, requester: User): Unit {
        teamRepository.findById(teamId)
            .orElseThrow { TeamNotFoundException("Team not found: $teamId") }
            .let { teamRepository.delete(it) }
    }
}
