package com.onmeet.auth.service

import com.onmeet.auth.dto.*
import com.onmeet.auth.entity.User
import com.onmeet.auth.exception.*
import com.onmeet.auth.repository.jpa.JobTitleRepository
import com.onmeet.auth.repository.jpa.UserRepository
import com.onmeet.common.exception.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.data.domain.Pageable

@Service
@Transactional(readOnly = true)
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val jobTitleRepository: JobTitleRepository
) : UserService {

    @Transactional
    override fun updateUserProfile(userId: Long, requester: User, request: UserProfileUpdateRequest): UserResponseDto {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found: $userId") }

        // Permission check: self or company manager
        if (!requester.isSelf(user) && !requester.isManager()) {
            throw InsufficientPermissionException("No permission to update this profile")
        }
        
        if (requester.isManager() && !user.belongsToCompany(requester.company.requireId())) {
            throw CrossCompanyAccessException("Manager can only update users in their own company")
        }

        request.name?.let { user.name = it }
        request.employeeId?.let { user.employeeId = it }
        request.jobTitleId?.let { titleId ->
            val jobTitle = jobTitleRepository.findById(titleId)
                .orElseThrow { JobTitleNotFoundException("JobTitle not found: $titleId") }
            if (!jobTitle.belongsToCompany(user.company.requireId())) {
                throw CompanyMismatchException("JobTitle does not belong to user's company")
            }
            user.jobTitle = jobTitle
        }

        return userRepository.save(user).toResponseDto()
    }

    override fun getUserInfo(userId: Long): UserResponseDto =
        userRepository.findById(userId)
            .map { it.toResponseDto() }
            .orElseThrow { UserNotFoundException("User not found: $userId") }

    override fun getUserInfo(email: String): UserResponseDto =
        userRepository.findByEmail(email)
            .map { it.toResponseDto() }
            .orElseThrow { UserNotFoundException("User not found with email: $email") }

    override fun getAllEmployees(manager: User, pageable: Pageable): PageResponse<UserResponseDto> =
        if (!manager.isManager()) {
            throw InsufficientPermissionException("Only managers can view all employees")
        } else {
            userRepository.findByCompany(manager.company, pageable)
                .let { PageResponse.from(it) { user -> user.toResponseDto() } }
        }

    override fun getCompanyIdByUserId(userId: Long): Long =
        userRepository.findById(userId)
            .map { it.company.requireId() }
            .orElseThrow { UserNotFoundException("User not found with ID: $userId") }

    @Transactional
    override fun deactivateUser(userId: Long, manager: User): UserResponseDto {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found: $userId") }

        validateManagerPermission(manager, user)
        user.deactivate()
        return userRepository.save(user).toResponseDto()
    }

    @Transactional
    override fun activateUser(userId: Long, manager: User): UserResponseDto {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found: $userId") }

        validateManagerPermission(manager, user)
        user.activate()
        return userRepository.save(user).toResponseDto()
    }

    private fun validateManagerPermission(manager: User, targetUser: User) {
        if (!manager.isManager()) {
            throw InsufficientPermissionException("Only managers can change user status")
        }
        if (!targetUser.belongsToCompany(manager.company.requireId())) {
            throw CrossCompanyAccessException("Manager can only manage users in their own company")
        }
    }
}
