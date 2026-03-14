package com.onmeet.auth.service

import com.onmeet.auth.client.FileClient
import com.onmeet.auth.dto.*
import com.onmeet.auth.dto.toResponseDto
import com.onmeet.auth.entity.User
import com.onmeet.auth.repository.jpa.UserRepository
import com.onmeet.common.dto.NotificationRequestDto
import com.onmeet.common.exception.BusinessException
import com.onmeet.common.exception.errorcode.AuthErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Facade for auth-related operations. Delegates to specialized services.
 * Controllers can depend on AuthService for backward compatibility, or
 * directly on the specific service (SignupService, AuthenticationService, etc.).
 */
@Service
class AuthService(
    private val signupService: SignupService,
    private val authenticationService: AuthenticationService,
    private val withdrawService: WithdrawService,
    private val passwordService: PasswordService,
    private val invitationService: InvitationService,
    private val fileClient: FileClient,
    private val userRepository: UserRepository,
    private val notificationEventPublisher: NotificationEventPublisher
) {
    private val log = LoggerFactory.getLogger(AuthService::class.java)

    @Transactional
    fun signupCompany(request: CompanySignupRequest, profileImage: org.springframework.web.multipart.MultipartFile?): Long =
        signupService.signupCompany(request, profileImage)

    @Transactional
    fun signupCompany(request: CompanySignupRequest): Long =
        signupService.signupCompany(request, null)

    @Transactional
    fun joinCompany(request: JoinRequest, profileImage: org.springframework.web.multipart.MultipartFile?): Long =
        signupService.joinCompany(request, profileImage)

    @Transactional
    fun joinCompany(request: JoinRequest): Long =
        signupService.joinCompany(request, null)

    @Transactional
    fun login(request: LoginRequest): TokenResponse =
        authenticationService.login(request)

    @Transactional
    fun guestLogin(request: GuestLoginRequest): TokenResponse =
        authenticationService.guestLogin(request)

    @Transactional
    fun refresh(token: String): TokenResponse =
        authenticationService.refresh(token)

    fun logout(accessToken: String?, email: String?) =
        authenticationService.logout(accessToken, email)

    @Transactional(readOnly = true)
    fun validateInvitation(email: String, code: String): InvitationResponse =
        invitationService.validateInvitation(email, code).toResponseDto()

    @Transactional
    fun withdraw(email: String, request: WithdrawRequest) =
        withdrawService.withdraw(email, request)

    @Transactional
    fun changePassword(email: String, request: ChangePasswordRequest) =
        passwordService.changePassword(email, request)

    @Transactional
    fun findPassword(email: String) =
        passwordService.findPassword(email)

    @Transactional
    fun resetUserProfileImage(targetUserId: Long, requesterEmail: String) {
        val targetUser = userRepository.findById(targetUserId)
            .orElseThrow { BusinessException(AuthErrorCode.USER_NOT_FOUND) }

        val requester = userRepository.findByEmail(requesterEmail)
            .orElseThrow { BusinessException(AuthErrorCode.REQUESTER_NOT_FOUND) }

        val isManager = requester.roles.contains(User.Role.MANAGER) &&
                requester.company.id == targetUser.company.id

        if (!isManager) {
            throw BusinessException(AuthErrorCode.PROFILE_RESET_FORBIDDEN)
        }

        targetUser.profileImageId?.let { oldImageId ->
            try {
                fileClient.deleteFile(oldImageId)
            } catch (e: Exception) {
                log.warn("Failed to delete old profile image: $oldImageId", e)
            }
        }

        fileClient.generateDefaultProfileImage(targetUser.name)?.let {
            targetUser.profileImageId = it.id
            userRepository.save(targetUser)
            notificationEventPublisher.publishNotification(
                NotificationRequestDto(
                    userId = targetUser.id,
                    type = "SYSTEM",
                    title = "프로필 초기화",
                    body = "관리자에 의해 프로필 이미지가 초기화되었습니다.",
                    actorUserId = requester.id
                )
            )
        }
    }
}
