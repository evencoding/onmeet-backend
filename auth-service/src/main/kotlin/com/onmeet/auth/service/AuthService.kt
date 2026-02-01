package com.onmeet.auth.service

import com.onmeet.auth.dto.CompanySignupRequest
import com.onmeet.auth.dto.JoinRequest
import com.onmeet.auth.dto.LoginRequest
import com.onmeet.auth.dto.SignupRequest
import com.onmeet.auth.dto.TokenResponse
import com.onmeet.auth.dto.TeamRequest
import com.onmeet.auth.dto.GuestLoginRequest
import com.onmeet.auth.entity.User
import com.onmeet.auth.exception.EmailAlreadyExistsException
import com.onmeet.auth.repository.UserRepository
import com.onmeet.auth.security.JwtTokenProvider
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

import com.onmeet.auth.repository.RefreshTokenRepository
import com.onmeet.auth.entity.RefreshToken
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authenticationManager: AuthenticationManager,
    private val jwtTokenProvider: JwtTokenProvider,
    private val companyService: CompanyService,
    private val invitationService: InvitationService,
    private val refreshTokenRepository: RefreshTokenRepository
) {

    companion object {
        private const val INITIAL_TEAM_COLOR = "#FFFFFF"
        private const val INITIAL_TEAM_DESCRIPTION = "Initial team"
    }

    @Transactional
    fun signup(request: SignupRequest): Long {
        if (userRepository.existsByEmail(request.email)) {
            throw EmailAlreadyExistsException("Email already in use")
        }

        val user = User(
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password),
            name = request.name
        )
        return userRepository.save(user).id ?: throw IllegalStateException("User ID not generated after save")
    }

    @Transactional
    fun signupCompany(request: CompanySignupRequest): Long {
        if (userRepository.existsByEmail(request.email)) {
            throw EmailAlreadyExistsException("Email already in use")
        }

        // 1. Create Company
        val company = companyService.createCompany(request.companyName)

        // 2. Create Initial Team (from request)
        val defaultTeam = companyService.createTeam(
            company.id ?: throw IllegalStateException("Company ID not generated"),
            TeamRequest(request.teamName, INITIAL_TEAM_DESCRIPTION, INITIAL_TEAM_COLOR)
        )

        // 3. Create User (Manager)
        val user = User(
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password),
            name = request.name,
            role = User.Role.MANAGER,
            company = company,
            team = defaultTeam,
            status = User.UserStatus.ACTIVE
        )

        return userRepository.save(user).id ?: throw IllegalStateException("User ID not generated")
    }

    @Transactional
    fun joinCompany(request: JoinRequest): Long {
        // 1. Validate Invitation
        val invitation = invitationService.validateInvitation(request.email, request.code)

        if (userRepository.existsByEmail(request.email)) {
            throw EmailAlreadyExistsException("Email already in use")
        }

        // 2. Create User
        val user = User(
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password),
            name = request.name,
            employeeId = request.employeeId,
            role = invitation.role,
            company = invitation.company,
            status = User.UserStatus.ACTIVE
        )

        val savedUser = userRepository.save(user)

        // 3. Mark Invitation as used
        invitationService.deleteInvitation(invitation.id ?: throw IllegalStateException("Invitation ID is null"))

        return savedUser.id ?: throw IllegalStateException("User ID not generated")
    }

    @Transactional
    fun login(request: LoginRequest): TokenResponse {
        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.email, request.password)
        )

        // Generate Access Token
        val accessToken = jwtTokenProvider.generateToken(authentication)

        // Generate Refresh Token
        val refreshTokenStr = UUID.randomUUID().toString()
        val authorities = authentication.authorities.joinToString(",") { it.authority }

        // Save to Redis
        val refreshToken = RefreshToken(
            mobileOrEmail = request.email,
            token = refreshTokenStr,
            authority = authorities
        )
        refreshTokenRepository.save(refreshToken)

        return TokenResponse(accessToken, refreshTokenStr)
    }

    fun guestLogin(request: GuestLoginRequest): TokenResponse {
        val accessToken = jwtTokenProvider.generateGuestToken(request.name, listOf("ROLE_GUEST"), request.meetingId)
        return TokenResponse(accessToken, null)
    }

    @Transactional
    fun refresh(token: String): TokenResponse {
        val refreshTokenEntity = refreshTokenRepository.findByToken(token)
            ?: throw IllegalArgumentException("Invalid refresh token")

        // 즉시 토큰을 삭제하여 재사용(경쟁 조건)을 방지합니다.
        refreshTokenRepository.delete(refreshTokenEntity)

        val user = userRepository.findByEmail(refreshTokenEntity.mobileOrEmail)
            .orElseThrow { IllegalArgumentException("User not found") }

        val authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
        val newAccessToken = jwtTokenProvider.generateToken(authentication)

        // Rotate Refresh Token
        val newRefreshTokenStr = UUID.randomUUID().toString()
        val newRefreshTokenEntity = RefreshToken(
            mobileOrEmail = user.email,
            token = newRefreshTokenStr,
            authority = refreshTokenEntity.authority
        )
        refreshTokenRepository.save(newRefreshTokenEntity)

        return TokenResponse(newAccessToken, newRefreshTokenStr)
    }

    @Transactional
    fun logout(userId: Long) {
        userRepository.findById(userId).ifPresent { user ->
            refreshTokenRepository.deleteById(user.email)
        }
    }
}
