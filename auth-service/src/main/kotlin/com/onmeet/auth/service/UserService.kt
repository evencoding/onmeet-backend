package com.onmeet.auth.service

import com.onmeet.auth.dto.PageResponse
import com.onmeet.auth.dto.UserInfoDto
import com.onmeet.auth.dto.UserProfileUpdateRequest
import com.onmeet.auth.dto.UserResponseDto
import com.onmeet.auth.entity.User
import org.springframework.data.domain.Pageable
import org.springframework.web.multipart.MultipartFile

interface UserService {
    fun updateUserProfile(userId: Long, requester: User, request: UserProfileUpdateRequest, profileImage: MultipartFile? = null): UserResponseDto
    fun getUserInfo(userId: Long, requester: User): UserResponseDto
    fun getUserInfo(email: String): UserResponseDto
    fun getAllEmployees(manager: User, pageable: Pageable): PageResponse<UserResponseDto>
    fun getInvitableMembers(user: User, pageable: Pageable): PageResponse<UserResponseDto>
    fun getCompanyIdByUserId(userId: Long): Long
    fun getUserPermissions(userId: Long): com.onmeet.auth.dto.UserPermissionResponse
    fun deactivateUser(userId: Long, manager: User): UserResponseDto
    fun activateUser(userId: Long, manager: User): UserResponseDto
    fun deleteMyProfileImage(requester: User): UserResponseDto
    fun getMyInfo(user: User): UserResponseDto

    // Internal API - No permission check
    fun getUserInfoById(userId: Long): UserInfoDto
    fun getBatchUserInfo(userIds: List<Long>): List<UserInfoDto>
    fun existsById(userId: Long): Boolean
    fun existsByIds(userIds: List<Long>): Map<Long, Boolean>
}
