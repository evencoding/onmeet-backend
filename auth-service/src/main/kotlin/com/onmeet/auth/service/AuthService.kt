package com.onmeet.auth.service

import com.onmeet.auth.dto.*
import com.onmeet.auth.dto.toResponseDto
import com.onmeet.auth.entity.User
import com.onmeet.auth.exception.*
import com.onmeet.auth.repository.jpa.UserRepository
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import com.onmeet.auth.repository.jpa.WithdrawnUserRepository
import com.onmeet.auth.entity.WithdrawnUser
import java.time.LocalDateTime
import org.slf4j.LoggerFactory

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authenticationManager: AuthenticationManager,
    private val companyService: CompanyService,
    private val teamService: TeamService,
    private val invitationService: InvitationService,
    private val jobTitleService: JobTitleService,
    private val tokenService: TokenService,
    private val fileClient: com.onmeet.auth.client.FileClient,
    private val withdrawnUserRepository: WithdrawnUserRepository,
    private val emailService: EmailService
) {
    companion object {
        private val log = LoggerFactory.getLogger(AuthService::class.java)
        private const val TEMP_PASSWORD_LENGTH = 8
        private const val TEMP_PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"
    }

    @Transactional
    fun signupCompany(request: CompanySignupRequest, profileImage: org.springframework.web.multipart.MultipartFile?): Long {
        if (userRepository.existsByEmail(request.email)) {
            throw EmailAlreadyExistsException("Email already in use: ${request.email}")
        }

        // 1. Create Company
        val company = companyService.createCompany(request.companyName)

        // 2. Create Default Job Title
        val defaultJobTitle = jobTitleService.createDefaultInitialTitle(company)

        // 3. Create User (Manager)
        val user = User(
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password),
            name = request.name,
            roles = mutableSetOf(User.Role.MANAGER),
            company = company,
            jobTitle = defaultJobTitle,
            status = User.UserStatus.ACTIVE
        )

        val savedUser = userRepository.save(user)

        // 4. Profile Image Logic
        processProfileImage(savedUser, profileImage)

        return savedUser.requireId()
    }

    @Transactional
    fun signupCompany(request: CompanySignupRequest): Long = signupCompany(request, null)

    @Transactional
    fun joinCompany(request: JoinRequest, profileImage: org.springframework.web.multipart.MultipartFile?): Long {
        // 1. Validate Invitation
        val invitation = invitationService.validateInvitation(request.email, request.code)

        if (userRepository.existsByEmail(request.email)) {
            throw EmailAlreadyExistsException("Email already in use: ${request.email}")
        }

        // 2. Assign Default Job Title
        val defaultJobTitle = jobTitleService.getDefaultJobTitle(invitation.company)

        // 3. Create User
        val user = User(
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password),
            name = request.name,
            employeeId = request.employeeId,
            roles = mutableSetOf(invitation.role),
            company = invitation.company,
            jobTitle = defaultJobTitle,
            status = User.UserStatus.ACTIVE
        )

        val savedUser = userRepository.save(user)

        // 4. Mark Invitation as used
        invitationService.deleteInvitation(invitation.requireId())

        // 5. Profile Image Logic
        processProfileImage(savedUser, profileImage)

        return savedUser.requireId()
    }

    @Transactional
    fun joinCompany(request: JoinRequest): Long = joinCompany(request, null)

    private fun processProfileImage(user: User, profileImage: org.springframework.web.multipart.MultipartFile?) {
        // [Bug-8 FIX] Delete old profile image if exists
        user.profileImageId?.let {
            try {
                fileClient.deleteMyProfileImage()
            } catch (e: Exception) {
                log.warn("기존 프로필 이미지 삭제 실패 (userId=${user.id}): ${e.message}")
            }
        }

        val fileResp = if (profileImage != null && !profileImage.isEmpty) {
            fileClient.uploadProfileImage(profileImage, user.requireId().toString())
        } else {
            fileClient.generateDefaultProfileImage(user.name)
        }

        fileResp?.let {
            user.profileImageId = it.id
            userRepository.save(user)
        }
    }

    @Transactional
    fun login(request: LoginRequest): TokenResponse {
        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.email, request.password)
        )

        // Save FCM device token if provided
        request.deviceToken?.let { token ->
            userRepository.findByEmail(request.email)?.let { user ->
                user.fcmDeviceToken = token
                userRepository.save(user)
                log.info("FCM device token updated for user: ${request.email}")
            }
        }

        return tokenService.issueTokens(authentication, request.email)
    }

    @Transactional
    fun guestLogin(request: GuestLoginRequest): TokenResponse {
        return tokenService.issueGuestTokens(request.name, request.meetingId)
    }

    @Transactional
    fun refresh(token: String): TokenResponse {
        return tokenService.refreshTokens(token)
    }

    @Transactional(readOnly = true)
    fun validateInvitation(email: String, code: String): InvitationResponse =
        invitationService.validateInvitation(email, code).toResponseDto()

    fun logout(accessToken: String?, email: String?) {
        tokenService.revokeTokens(accessToken, email)
    }


    @Transactional
    fun resetUserProfileImage(targetUserId: Long, requesterEmail: String) {
        val targetUser = userRepository.findById(targetUserId)
            .orElseThrow { UserNotFoundException("User not found: $targetUserId") }
        
        val requester = userRepository.findByEmail(requesterEmail)
            .orElseThrow { UserNotFoundException("Requester not found: $requesterEmail") }

        // 권한 체크: 같은 회사의 매니저여야 함
        val isManager = requester.roles.contains(User.Role.MANAGER) && 
                        requester.company.id == targetUser.company.id

        if (!isManager) {
            throw UnauthorizedException("Only managers can reset other users' profile images.")
        }

        // 기존 이미지가 있다면 삭제 요청
        targetUser.profileImageId?.let { oldImageId ->
            try {
                fileClient.deleteFile(oldImageId)
            } catch (e: Exception) {
                // 파일 삭제 실패가 비즈니스 로직을 방해하지 않도록 로그만 남김
                // 실제 운영에서는 재시도 큐에 넣거나 처리가 필요할 수 있음
                // log.warn("Failed to delete old profile image: $oldImageId", e)
            }
        }

        // 기본 이미지 생성 및 할당
        fileClient.generateDefaultProfileImage(targetUser.name)?.let {
            targetUser.profileImageId = it.id
            userRepository.save(targetUser)
        }
    }

    @Transactional
    fun withdraw(email: String, request: WithdrawRequest) {
        val user = userRepository.findByEmail(email)
            .orElseThrow { UserNotFoundException("User not found: $email") }

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw InvalidPasswordException("Invalid password")
        }

        // Archive user data
        val withdrawnUser = WithdrawnUser(
            originalUserId = user.requireId(),
            email = user.email,
            name = user.name,
            reason = request.reason,
            withdrawnAt = java.time.LocalDateTime.now()
        )
        withdrawnUserRepository.save(withdrawnUser)

        // [Bug-1 FIX] Delete profile image from file-service
        user.profileImageId?.let {
            try {
                fileClient.deleteMyProfileImage()
            } catch (e: Exception) {
                log.warn("탈퇴 처리 중 프로필 이미지 삭제 실패 (userId=${user.id}): ${e.message}")
            }
        }

        // Anonymize and deactivate user
        user.email = "withdrawn_${user.id}@onmeet.deleted"
        user.name = "Withdrawn User"
        user.passwordHash = "" // Clear password
        user.status = User.UserStatus.INACTIVE
        user.profileImageId = null

        userRepository.save(user)

        // [Bug-2 FIX] Revoke all tokens for this user
        tokenService.revokeTokens(null, email)
    }

    @Transactional
    fun changePassword(email: String, request: ChangePasswordRequest) {
        val user = userRepository.findByEmail(email)
            .orElseThrow { UserNotFoundException("User not found: $email") }

        if (!passwordEncoder.matches(request.oldPassword, user.passwordHash)) {
            throw InvalidPasswordException("Old password does not match")
        }

        user.passwordHash = passwordEncoder.encode(request.newPassword)
        user.isPasswordReset = false  // Reset password reset flag
        userRepository.save(user)
        log.info("Password changed successfully for user: $email, isPasswordReset flag reset to false")
    }

    @Transactional
    fun findPassword(email: String) {
        val user = userRepository.findByEmail(email)
            .orElseThrow { UserNotFoundException("User not found: $email") }

        // Generate 8-character temporary password
        val temporaryPassword = generateTemporaryPassword()

        // Encode and save temporary password
        user.passwordHash = passwordEncoder.encode(temporaryPassword)
        user.isPasswordReset = true
        userRepository.save(user)

        // Send temporary password via email
        emailService.sendTemporaryPassword(email, temporaryPassword, user.name)

        log.info("Temporary password generated and sent for user: $email")
    }

    private fun generateTemporaryPassword(): String {
        val random = java.security.SecureRandom()
        return (1..TEMP_PASSWORD_LENGTH)
            .map { TEMP_PASSWORD_CHARS[random.nextInt(TEMP_PASSWORD_CHARS.length)] }
            .joinToString("")
    }
}
