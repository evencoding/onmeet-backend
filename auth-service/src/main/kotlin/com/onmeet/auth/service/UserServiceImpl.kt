package com.onmeet.auth.service

import com.onmeet.auth.dto.*
import com.onmeet.auth.entity.User
import com.onmeet.auth.exception.*
import com.onmeet.auth.repository.jpa.JobTitleRepository
import com.onmeet.auth.repository.jpa.UserRepository
import com.onmeet.common.exception.BusinessException
import com.onmeet.common.exception.errorcode.AuthErrorCode
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.data.domain.Pageable
import org.springframework.web.multipart.MultipartFile

@Service
@Transactional(readOnly = true)
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val jobTitleRepository: JobTitleRepository,
    private val fileClient: com.onmeet.auth.client.FileClient,
    private val notificationEventPublisher: NotificationEventPublisher
) : UserService {

    companion object {
        private val log = LoggerFactory.getLogger(UserServiceImpl::class.java)
    }

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
            .orElseThrow { BusinessException(AuthErrorCode.USER_NOT_FOUND) }

        // Permission check: self or company manager
        if (!requester.isSelf(user) && !requester.isManager()) {
            throw BusinessException(AuthErrorCode.PROFILE_UPDATE_FORBIDDEN)
        }

        if (requester.isManager() && !user.belongsToCompany(requester.company.requireId())) {
            throw BusinessException(AuthErrorCode.CROSS_COMPANY_ACCESS)
        }

        // Update text fields
        request.name?.let { user.name = it }
        request.employeeId?.let { user.employeeId = it }
        request.jobTitleId?.let { titleId ->
            val jobTitle = jobTitleRepository.findById(titleId)
                .orElseThrow { BusinessException(AuthErrorCode.JOB_TITLE_NOT_FOUND) }
            if (!jobTitle.belongsToCompany(user.company.requireId())) {
                throw BusinessException(AuthErrorCode.JOB_TITLE_COMPANY_MISMATCH)
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
                    log.error("Failed to delete old profile image for user ${user.requireId()}", e)
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
            .orElseThrow { BusinessException(AuthErrorCode.USER_NOT_FOUND) }

        if (!user.belongsToCompany(requester.company.requireId())) {
            throw BusinessException(AuthErrorCode.CROSS_COMPANY_ACCESS)
        }

        return user.toResponseDto()
    }

    override fun getUserInfo(email: String): UserResponseDto =
        userRepository.findByEmail(email)
            .map { it.toResponseDto() }
            .orElseThrow { BusinessException(AuthErrorCode.USER_NOT_FOUND) }

    override fun getAllEmployees(manager: User, pageable: Pageable): PageResponse<UserResponseDto> =
        if (!manager.isManager()) {
            throw BusinessException(AuthErrorCode.EMPLOYEE_LIST_FORBIDDEN)
        } else {
            userRepository.findByCompany(manager.company, pageable)
                .let { PageResponse.from(it) { user -> user.toResponseDto() } }
        }

    override fun getCompanyIdByUserId(userId: Long): Long =
        userRepository.findById(userId)
            .map { it.company.requireId() }
            .orElseThrow { BusinessException(AuthErrorCode.USER_NOT_FOUND) }

    override fun getUserPermissions(userId: Long): UserPermissionResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { BusinessException(AuthErrorCode.USER_NOT_FOUND) }

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
            .orElseThrow { BusinessException(AuthErrorCode.USER_NOT_FOUND) }

        validateManagerPermission(manager, user)
        user.deactivate()
        val deactivatedUser = userRepository.save(user)
        // 계정 비활성화 알림 전송
        notificationEventPublisher.publishNotification(
            com.onmeet.common.dto.NotificationRequestDto(
                userId = deactivatedUser.id,
                type = "SYSTEM",
                title = "계정 비활성화",
                body = "관리자에 의해 계정이 비활성화되었습니다.",
                actorUserId = manager.id
            )
        )
        return deactivatedUser.toResponseDto()
    }

    @Transactional
    @CacheEvict(value = ["userInfo"], key = "#userId")
    override fun activateUser(userId: Long, manager: User): UserResponseDto {
        val user = userRepository.findById(userId)
            .orElseThrow { BusinessException(AuthErrorCode.USER_NOT_FOUND) }

        validateManagerPermission(manager, user)
        user.activate()
        val activatedUser = userRepository.save(user)
        // 계정 활성화 알림 전송
        notificationEventPublisher.publishNotification(
            com.onmeet.common.dto.NotificationRequestDto(
                userId = activatedUser.id,
                type = "SYSTEM",
                title = "계정 활성화",
                body = "관리자에 의해 계정이 활성화되었습니다.",
                actorUserId = manager.id
            )
        )
        return activatedUser.toResponseDto()
    }

    override fun getMyInfo(user: User): UserResponseDto {
        return user.toResponseDto()
    }

    // Internal API - No permission check
    override fun getUserInfoById(userId: Long): UserInfoDto {
        val user = userRepository.findById(userId)
            .orElseThrow { BusinessException(AuthErrorCode.USER_NOT_FOUND) }
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

    // Internal API - No permission check
    override fun getBatchFcmTokens(userIds: List<Long>): Map<Long, List<String>> {
        if (userIds.isEmpty()) return emptyMap()
        val tokens = userRepository.findFcmTokensByUserIds(userIds)
        return tokens.associate { it.id to listOf(it.fcmDeviceToken) }
    }

    private fun validateManagerPermission(manager: User, targetUser: User) {
        if (!manager.isManager()) {
            throw BusinessException(AuthErrorCode.USER_STATUS_CHANGE_FORBIDDEN)
        }
        if (!targetUser.belongsToCompany(manager.company.requireId())) {
            throw BusinessException(AuthErrorCode.CROSS_COMPANY_MANAGE_FORBIDDEN)
        }
    }
}
