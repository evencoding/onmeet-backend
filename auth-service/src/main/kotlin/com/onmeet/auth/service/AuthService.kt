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
import com.onmeet.auth.repository.WithdrawnUserRepository
import com.onmeet.auth.entity.WithdrawnUser
import java.time.LocalDateTime

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
    private val withdrawnUserRepository: com.onmeet.auth.repository.WithdrawnUserRepository
) {

    @Transactional
    fun signupCompany(request: CompanySignupRequest, profileImage: org.springframework.web.multipart.MultipartFile?): Long {
        if (userRepository.existsByEmail(request.email)) {
            throw EmailAlreadyExistsException("Email already in use: ${request.email}")
        }

        // 1. Create Company
        val company = companyService.createCompany(request.companyName)

        // 2. Create Initial Team (from request)
        val defaultTeam = teamService.createTeam(
            company.requireId(),
            request.teamName
        )

        // 3. Create Default Job Title
        val defaultJobTitle = jobTitleService.createDefaultInitialTitle(company)

        // 4. Create User (Manager)
        val user = User(
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password),
            name = request.name,
            roles = mutableSetOf(User.Role.MANAGER),
            company = company,
            teams = mutableSetOf(defaultTeam),
            jobTitle = defaultJobTitle,
            status = User.UserStatus.ACTIVE
        )

        val savedUser = userRepository.save(user)
        
        // 5. Profile Image Logic
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

        // 권한 체크: 본인이거나, 같은 회사의 매니저여야 함
        val isSelf = targetUser.id == requester.id
        val isManager = requester.roles.contains(User.Role.MANAGER) && 
                        requester.company.id == targetUser.company.id

        if (!isSelf && !isManager) {
            throw UnauthorizedException("You do not have permission to modify this user's profile.")
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

        val name = if (isSelf) "Deleted User" else targetUser.name 

        // 기본 이미지 생성 및 할당
        fileClient.generateDefaultProfileImage(name)?.let {
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
        val withdrawnUser = com.onmeet.auth.entity.WithdrawnUser(
            originalUserId = user.requireId(),
            email = user.email,
            name = user.name,
            reason = request.reason,
            withdrawnAt = java.time.LocalDateTime.now()
        )
        withdrawnUserRepository.save(withdrawnUser)

        // Anonymize and deactivate user
        user.email = "withdrawn_${user.id}@onmeet.deleted"
        user.name = "Withdrawn User"
        user.passwordHash = "" // Clear password
        user.status = User.UserStatus.INACTIVE
        user.profileImageId = null 
        // Logic to delete profile image from file-service can be added here if needed.
        
        userRepository.save(user)
    }

    @Transactional
    fun updateProfile(email: String, request: UpdateProfileRequest) {
        val user = userRepository.findByEmail(email)
            .orElseThrow { UserNotFoundException("User not found: $email") }

        request.name?.let {
            if (it.isNotBlank()) user.name = it
        }

        request.jobTitle?.let { titleName ->
            if (titleName.isBlank()) {
                user.jobTitle = null
            } else {
                val jobTitle = jobTitleService.getJobTitleByName(user.company, titleName)
                    ?: throw JobTitleNotFoundException("Job title not found: $titleName")
                user.jobTitle = jobTitle
            }
        }
        
        userRepository.save(user)
    }

    @Transactional
    fun changePassword(email: String, request: ChangePasswordRequest) {
        val user = userRepository.findByEmail(email)
            .orElseThrow { UserNotFoundException("User not found: $email") }

        if (!passwordEncoder.matches(request.oldPassword, user.passwordHash)) {
            throw InvalidPasswordException("Old password does not match")
        }

        user.passwordHash = passwordEncoder.encode(request.newPassword)
        userRepository.save(user)
    }
}
