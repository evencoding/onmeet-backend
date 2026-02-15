package com.onmeet.auth.service

import com.onmeet.auth.config.TeamProperties
import com.onmeet.auth.dto.TeamRequest
import com.onmeet.auth.entity.Team
import com.onmeet.auth.entity.User
import com.onmeet.auth.entity.Company
import com.onmeet.common.exception.InsufficientPermissionException
import com.onmeet.auth.exception.*
import com.onmeet.auth.repository.jpa.TeamRepository
import com.onmeet.auth.repository.jpa.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TeamServiceImpl(
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository,
    private val companyRepository: com.onmeet.auth.repository.jpa.CompanyRepository,
    private val teamProperties: TeamProperties
) : TeamService {

    @Transactional
    override fun createTeam(user: User, request: TeamRequest): Team {
        val company = user.company
        
        if (teamRepository.findByNameAndCompanyId(request.name, company.requireId()) != null) {
            throw TeamAlreadyExistsException("Team already exists in this company: ${request.name}")
        }

        val initialStatus = if (user.roles.contains(User.Role.MANAGER)) {
            Team.TeamStatus.ACTIVE
        } else {
            Team.TeamStatus.PENDING_APPROVAL
        }

        val team = Team(
            name = request.name,
            description = request.description,
            color = request.color,
            company = company,
            leader = if (initialStatus == Team.TeamStatus.ACTIVE) user else null,
            status = initialStatus
        )

        val savedTeam = teamRepository.save(team)
        
        if (initialStatus == Team.TeamStatus.ACTIVE) {
             user.teams.add(savedTeam)
             userRepository.save(user)
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
        teamRepository.findById(teamId)
            .orElseThrow { TeamNotFoundException("Team not found: $teamId") }
            .apply { status = Team.TeamStatus.ACTIVE }
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

        team.leader = newLeader
        newLeader.roles.add(User.Role.TEAM_LEADER)
        newLeader.teams.add(team)
        userRepository.save(newLeader)
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

        team.leader = newLeader
        newLeader.roles.add(User.Role.TEAM_LEADER)
        newLeader.teams.add(team)
        
        userRepository.save(newLeader)
        teamRepository.save(team)
    }

    @Transactional
    override fun dissolveTeam(teamId: Long, requester: User): Unit {
        teamRepository.findById(teamId)
            .orElseThrow { TeamNotFoundException("Team not found: $teamId") }
            .let { teamRepository.delete(it) }
    }
}
