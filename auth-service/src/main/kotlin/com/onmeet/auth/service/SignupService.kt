package com.onmeet.auth.service

import com.onmeet.auth.client.FileClient
import com.onmeet.auth.dto.CompanySignupRequest
import com.onmeet.auth.dto.JoinRequest
import com.onmeet.auth.entity.User
import com.onmeet.auth.repository.jpa.UserRepository
import com.onmeet.common.exception.BusinessException
import com.onmeet.common.exception.errorcode.AuthErrorCode
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@Service
class SignupService(
    private val companyService: CompanyService,
    private val invitationService: InvitationService,
    private val jobTitleService: JobTitleService,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val fileClient: FileClient
) {
    @Transactional
    fun signupCompany(request: CompanySignupRequest, profileImage: MultipartFile?): Long {
        val savedUser = signupCompanyInternal(request)
        processProfileImage(savedUser, profileImage)
        return savedUser.requireId()
    }

    fun signupCompanyInternal(request: CompanySignupRequest): User {
        if (userRepository.existsByEmail(request.email)) {
            throw BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS)
        }

        val company = companyService.createCompany(request.companyName)
        val defaultJobTitle = jobTitleService.createDefaultInitialTitle(company)

        val user = User(
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password),
            name = request.name,
            roles = mutableSetOf(User.Role.MANAGER),
            company = company,
            jobTitle = defaultJobTitle,
            status = User.UserStatus.ACTIVE
        )

        return userRepository.save(user)
    }

    @Transactional
    fun joinCompany(request: JoinRequest, profileImage: MultipartFile?): Long {
        val savedUser = joinCompanyInternal(request)
        processProfileImage(savedUser, profileImage)
        return savedUser.requireId()
    }

    fun joinCompanyInternal(request: JoinRequest): User {
        val invitation = invitationService.validateInvitation(request.email, request.code)

        if (userRepository.existsByEmail(request.email)) {
            throw BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS)
        }

        val defaultJobTitle = jobTitleService.getDefaultJobTitle(invitation.company)

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
        invitationService.deleteInvitation(invitation.requireId())
        return savedUser
    }

    fun processProfileImage(user: User, profileImage: MultipartFile?) {
        fileClient.safeDeleteProfileImageIfPresent(user.profileImageId, "userId=${user.id}")

        val fileResp = if (profileImage != null && !profileImage.isEmpty) {
            fileClient.uploadProfileImage(profileImage, user.requireId().toString())
        } else {
            fileClient.generateDefaultProfileImage(user.name, user.requireId().toString())
        }

        fileResp?.let {
            user.profileImageId = it.id
            userRepository.save(user)
        }
    }
}
