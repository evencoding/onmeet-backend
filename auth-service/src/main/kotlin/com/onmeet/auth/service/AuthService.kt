package com.onmeet.auth.service

import com.onmeet.auth.dto.CompanySignupRequest
import com.onmeet.auth.config.TeamProperties
import com.onmeet.auth.dto.JoinRequest
import com.onmeet.auth.dto.LoginRequest

import com.onmeet.auth.dto.TokenResponse
import com.onmeet.auth.dto.TeamRequest
import com.onmeet.auth.dto.GuestLoginRequest
import com.onmeet.auth.dto.InvitationResponse
import com.onmeet.auth.entity.User
import com.onmeet.auth.exception.EmailAlreadyExistsException
import com.onmeet.common.exception.EntityNotFoundException
import com.onmeet.auth.repository.jpa.UserRepository
import com.onmeet.auth.security.JwtTokenProvider
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit
import org.springframework.data.redis.core.StringRedisTemplate

import com.onmeet.auth.repository.redis.RefreshTokenRepository
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
    private val refreshTokenRepository: RefreshTokenRepository,
    private val teamProperties: TeamProperties,
    private val redisTemplate: StringRedisTemplate
) {

    companion object {
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
            TeamRequest(request.teamName, teamProperties.initialDescription, teamProperties.initialColor)
        )

        // 3. Create User (Manager)
        val user = User(
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password),
            name = request.name,
            roles = mutableSetOf(User.Role.MANAGER),
            company = company,
            teams = mutableSetOf(defaultTeam),
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
            roles = mutableSetOf(invitation.role),
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

    @Transactional
    fun guestLogin(request: GuestLoginRequest): TokenResponse {
        val accessToken = jwtTokenProvider.generateGuestToken(request.name, listOf("ROLE_GUEST"), request.meetingId)

        // Generate Refresh Token
        val refreshTokenStr = UUID.randomUUID().toString()
        val authorities = "ROLE_GUEST"

        // Save to Redis with 1 day TTL (86400 seconds)
        val refreshToken = RefreshToken(
            mobileOrEmail = request.name,
            token = refreshTokenStr,
            authority = authorities,
            expiration = 86400L
        )
        refreshTokenRepository.save(refreshToken)

        return TokenResponse(accessToken, refreshTokenStr)
    }

    @Transactional
    fun refresh(token: String): TokenResponse {
        val refreshTokenEntity = refreshTokenRepository.findByToken(token)
            ?: throw IllegalArgumentException("Invalid refresh token")

        // 즉시 토큰을 삭제하여 재사용(경쟁 조건)을 방지합니다.
        refreshTokenRepository.delete(refreshTokenEntity)


        val user = userRepository.findByEmail(refreshTokenEntity.mobileOrEmail)
            .orElseThrow { EntityNotFoundException("User not found") }

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

    @Transactional(readOnly = true)
    fun validateInvitation(email: String, code: String): InvitationResponse {
        val invitation = invitationService.validateInvitation(email, code)
        return InvitationResponse(
            email = invitation.email,
            companyName = invitation.company.name,
            role = invitation.role
        )
    }

    fun logout(accessToken: String?, email: String?) {
        // 1. Blacklist Access Token
        if (!accessToken.isNullOrBlank()) {
            val remainingTime = jwtTokenProvider.getRemainingTime(accessToken)
            if (remainingTime > 0) {
                redisTemplate.opsForValue().set(
                    "blacklist:$accessToken",
                    "logout",
                    remainingTime,
                    TimeUnit.MILLISECONDS
                )
            }
        }

        // 2. Remove Refresh Token
        email?.let {
            refreshTokenRepository.deleteById(it)
        }
    }
}
