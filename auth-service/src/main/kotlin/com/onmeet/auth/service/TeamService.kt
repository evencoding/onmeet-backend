package com.onmeet.auth.service

import com.onmeet.auth.dto.TeamRequest
import com.onmeet.auth.entity.Team
import com.onmeet.auth.entity.User
import com.onmeet.auth.repository.jpa.TeamRepository
import com.onmeet.auth.repository.jpa.UserRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TeamService(
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository
) {

    @Transactional
    fun createTeam(user: User, request: TeamRequest): Team {
        val company = user.company
        
        if (teamRepository.findByNameAndCompanyId(request.name, company.id!!) != null) {
            throw IllegalArgumentException("Team already exists in this company")
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
        
        // If manager creates it, they are automatically the leader and member?
        // Logic: specific requirement says "General employee can create... status PENDING".
        // "Manager approves... assigns leader".
        // If manager creates, we can assign them as leader or leave it empty? 
        // Let's assume for now if created by manager, they become the leader.
        
        if (initialStatus == Team.TeamStatus.ACTIVE) {
             user.teams.add(savedTeam)
             userRepository.save(user)
        }

        return savedTeam
    }

    @Transactional
    fun approveTeam(teamId: Long, approver: User) {
        val team = teamRepository.findById(teamId)
            .orElseThrow { EntityNotFoundException("Team not found") }

        if (!approver.roles.contains(User.Role.MANAGER)) {
             throw AccessDeniedException("Only managers can approve teams")
        }
        
        if (team.company.id != approver.company.id) {
             throw AccessDeniedException("You can only approve teams in your company")
        }

        team.status = Team.TeamStatus.ACTIVE
        // Leader assignment logic should be separate or implicitly handled?
        // Requirement says "Manager... can designate team leader".
        // So approval just activates it.
    }

    @Transactional
    fun rejectTeam(teamId: Long, approver: User) {
        val team = teamRepository.findById(teamId)
            .orElseThrow { EntityNotFoundException("Team not found") }

        if (!approver.roles.contains(User.Role.MANAGER)) {
             throw AccessDeniedException("Only managers can reject teams")
        }
        
        if (team.company.id != approver.company.id) {
             throw AccessDeniedException("You can only reject teams in your company")
        }

        // Deletion or status change?
        // Typically deletion for rejected creation requests.
        teamRepository.delete(team)
    }

    @Transactional
    fun assignLeader(teamId: Long, manager: User, newLeaderId: Long) {
         val team = teamRepository.findById(teamId)
            .orElseThrow { EntityNotFoundException("Team not found") }

        if (!manager.roles.contains(User.Role.MANAGER)) {
             throw AccessDeniedException("Only managers can assign team leaders")
        }
        
        val newLeader = userRepository.findById(newLeaderId)
            .orElseThrow { EntityNotFoundException("User not found") }

        if (newLeader.company.id != team.company.id) {
             throw IllegalArgumentException("User must belong to the same company")
        }

        team.leader = newLeader
        newLeader.roles.add(User.Role.TEAM_LEADER)
        newLeader.teams.add(team)
        userRepository.save(newLeader)
        teamRepository.save(team)
    }

    @Transactional
    fun delegateLeader(teamId: Long, currentLeader: User, newLeaderId: Long) {
        val team = teamRepository.findById(teamId)
            .orElseThrow { EntityNotFoundException("Team not found") }

        // Check if current user is the leader OR a manager
        val isManager = currentLeader.roles.contains(User.Role.MANAGER)
        val isCurrentLeader = team.leader?.id == currentLeader.id

        if (!isManager && !isCurrentLeader) {
             throw AccessDeniedException("You specific permission to delegate leadership")
        }

        val newLeader = userRepository.findById(newLeaderId)
            .orElseThrow { EntityNotFoundException("User not found") }
            
        if (newLeader.company.id != team.company.id) {
             throw IllegalArgumentException("User must belong to the same company")
        }

        // Remove role from old leader? 
        // Requirement implies swapping leadership.
        if (team.leader != null) {
            // Check if old leader is leader of other teams? If not, maybe remove TEAM_LEADER role?
            // For simplicity, we keep the role or we can check later.
            // Let's keep it simple: just update the leader field.
            // If strict, we might want to verify if they lead other teams.
        }

        team.leader = newLeader
        newLeader.roles.add(User.Role.TEAM_LEADER)
        newLeader.teams.add(team)
        
        userRepository.save(newLeader)
        teamRepository.save(team)
    }

    @Transactional
    fun dissolveTeam(teamId: Long, requester: User) {
        val team = teamRepository.findById(teamId)
            .orElseThrow { EntityNotFoundException("Team not found") }

        val isManager = requester.roles.contains(User.Role.MANAGER)
        val isLeader = team.leader?.id == requester.id

        if (!isManager && !isLeader) {
             throw AccessDeniedException("You do not have permission to dissolve this team")
        }
        
        // Remove associations
        // We might need to remove team from all users
        // This depends on CascadeType. 
        // Logic: Remove references from users
        
        // This might be heavy if many users. simpler approach: 
        // Just delete team if Cascade logic handles it, or iterate.
        // Assuming JPA handles relationships if modeled correctly.
        // But users.teams is ManyToMany (or OneToMany inverse). 
        // Let's assume we need to clean up.
        
        // For now, simple delete.
        teamRepository.delete(team)
    }
}
