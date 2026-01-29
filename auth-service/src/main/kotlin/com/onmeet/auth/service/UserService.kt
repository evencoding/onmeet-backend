package com.onmeet.auth.service

import com.onmeet.auth.dto.CompanyInfoDto
import com.onmeet.auth.dto.TeamInfoDto
import com.onmeet.auth.dto.UserResponseDto
import com.onmeet.auth.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository
) {

    fun getUserInfo(userId: Long): UserResponseDto {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found") }

        return UserResponseDto(
            id = user.id!!,
            email = user.email,
            name = user.name,
            employeeId = user.employeeId,
            role = user.role.name,
            status = user.status.name,
            company = user.company?.let { CompanyInfoDto(it.id!!, it.name) },
            team = user.team?.let { TeamInfoDto(it.id!!, it.name, it.color) }
        )
    }
}
