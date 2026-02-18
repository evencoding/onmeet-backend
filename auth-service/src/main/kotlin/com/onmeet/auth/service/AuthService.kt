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

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authenticationManager: AuthenticationManager,
    private val companyService: CompanyService,
    private val teamService: TeamService,
    private val invitationService: InvitationService,
    private val jobTitleService: JobTitleService,
    private val tokenService: TokenService
) {

    @Transactional
    fun signupCompany(request: CompanySignupRequest): Long {
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

        return userRepository.save(user).requireId()
    }

    @Transactional
    fun joinCompany(request: JoinRequest): Long {
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

        return savedUser.requireId()
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
}
