package com.onmeet.auth.service

import com.onmeet.auth.dto.CompanyInfoDto
import com.onmeet.auth.dto.TeamInfoDto
import com.onmeet.auth.dto.UserResponseDto
import com.onmeet.auth.entity.User
import com.onmeet.auth.repository.jpa.UserRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository
) {

    fun getUserInfo(userId: Long): UserResponseDto {
        val user = userRepository.findById(userId)
            .orElseThrow { EntityNotFoundException("User not found: $userId") }

        return UserResponseDto(
            id = user.id ?: throw IllegalStateException("User ID cannot be null for a persisted entity"),
            email = user.email,
            name = user.name,
            employeeId = user.employeeId,
            role = user.role.name,
            status = user.status.name,
            company = user.company.let { it.id?.let { id -> CompanyInfoDto(id, it.name) } ?: throw IllegalStateException("Company ID cannot be null") },
            teams = user.teams.map { 
                it.id?.let { id -> TeamInfoDto(id, it.name, it.color) } ?: throw IllegalStateException("Team ID cannot be null") 
            }
        )
    }

    fun getCompanyIdByUserId(userId: Long): Long {
        val user = userRepository.findById(userId)
            .orElseThrow { EntityNotFoundException("User not found with ID: $userId") }
        return user.company?.id ?: throw EntityNotFoundException("User is not associated with any company")
    }
}
