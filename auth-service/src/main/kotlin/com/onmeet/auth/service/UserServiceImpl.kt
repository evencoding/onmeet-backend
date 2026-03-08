package com.onmeet.auth.service

import com.onmeet.auth.dto.*
import com.onmeet.auth.entity.User
import com.onmeet.auth.exception.*
import com.onmeet.auth.repository.jpa.JobTitleRepository
import com.onmeet.auth.repository.jpa.UserRepository
import com.onmeet.common.exception.CrossCompanyAccessException
import com.onmeet.common.exception.EntityNotFoundException
import com.onmeet.common.exception.InsufficientPermissionException
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.data.domain.Pageable
import org.springframework.web.multipart.MultipartFile

@Service
@Transactional(readOnly = true)
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val jobTitleRepository: JobTitleRepository,
    private val fileClient: com.onmeet.auth.client.FileClient
) : UserService {

    @Transactional
    @CacheEvict(value = ["userInfo"], key = "#requester.id")
    override fun deleteMyProfileImage(requester: User): UserResponseDto {
        fileClient.deleteMyProfileImage()
        requester.profileImageId = null
        return userRepository.save(requester).toResponseDto()
    }

    @Transactional
    @CacheEvict(value = ["userInfo"], key = "#userId")
    override fun updateUserProfile(userId: Long, requester: User, request: UserProfileUpdateRequest, profileImage: MultipartFile?): UserResponseDto {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found: $userId") }

        // Permission check: self or company manager
        if (!requester.isSelf(user) && !requester.isManager()) {
            throw InsufficientPermissionException("No permission to update this profile")
        }

        if (requester.isManager() && !user.belongsToCompany(requester.company.requireId())) {
            throw CrossCompanyAccessException("Manager can only update users in their own company")
        }

        // Update text fields
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

        // Handle profile image upload if provided
        if (profileImage != null && !profileImage.isEmpty) {
            // Delete old profile image if exists
            user.profileImageId?.let { oldImageId ->
                try {
                    fileClient.deleteMyProfileImage()
                } catch (e: Exception) {
                    // Log error but continue with upload (old image cleanup failed)
                    org.slf4j.LoggerFactory.getLogger(UserServiceImpl::class.java)
                        .error("Failed to delete old profile image for user ${user.requireId()}", e)
                }
            }

            // Upload new profile image
            val uploadResult = fileClient.uploadProfileImage(profileImage, user.requireId().toString())
            uploadResult?.let { user.profileImageId = it.id }
        }

        return userRepository.save(user).toResponseDto()
    }

    override fun getUserInfo(userId: Long, requester: User): UserResponseDto {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found: $userId") }

        if (!user.belongsToCompany(requester.company.requireId())) {
            throw CrossCompanyAccessException("You cannot access user info from another company")
        }

        return user.toResponseDto()
    }

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

    override fun getUserPermissions(userId: Long): UserPermissionResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found: $userId") }

        return UserPermissionResponse(
            userId = user.requireId(),
            roles = user.roles.map { it.name }.toSet(),
            companyId = user.company.id,
            teamIds = user.getTeams().mapNotNull { it.id }
        )
    }

    @Transactional
    @CacheEvict(value = ["userInfo"], key = "#userId")
    override fun deactivateUser(userId: Long, manager: User): UserResponseDto {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found: $userId") }

        validateManagerPermission(manager, user)
        user.deactivate()
        return userRepository.save(user).toResponseDto()
    }

    @Transactional
    @CacheEvict(value = ["userInfo"], key = "#userId")
    override fun activateUser(userId: Long, manager: User): UserResponseDto {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found: $userId") }

        validateManagerPermission(manager, user)
        user.activate()
        return userRepository.save(user).toResponseDto()
    }

    @Cacheable(value = ["userInfo"], key = "#user.id")
    override fun getMyInfo(user: User): UserResponseDto {
        return user.toResponseDto()
    }

    // Internal API - No permission check
    override fun getUserInfoById(userId: Long): UserInfoDto {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found: $userId") }
        return user.toUserInfoDto()
    }

    // Internal API - No permission check
    override fun getBatchUserInfo(userIds: List<Long>): List<UserInfoDto> {
        val users = userRepository.findAllById(userIds)
        return users.map { it.toUserInfoDto() }
    }

    // Internal API - No permission check
    override fun existsById(userId: Long): Boolean {
        return userRepository.existsById(userId)
    }

    // Internal API - No permission check
    override fun existsByIds(userIds: List<Long>): Map<Long, Boolean> {
        val existingIds = userRepository.findAllById(userIds).map { it.requireId() }.toSet()
        return userIds.associateWith { it in existingIds }
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
