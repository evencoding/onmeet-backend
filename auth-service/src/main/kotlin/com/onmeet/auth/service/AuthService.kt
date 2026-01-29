package com.onmeet.auth.service

import com.onmeet.auth.dto.CompanySignupRequest
import com.onmeet.auth.dto.JoinRequest
import com.onmeet.auth.dto.LoginRequest
import com.onmeet.auth.dto.TokenResponse
import com.onmeet.auth.entity.User
import com.onmeet.auth.repository.UserRepository
import com.onmeet.auth.security.JwtTokenProvider
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
    private val jwtTokenProvider: JwtTokenProvider,
    private val companyService: CompanyService,
    private val invitationService: InvitationService,
    private val refreshTokenRepository: com.onmeet.auth.repository.RefreshTokenRepository
) {

    // ... (signup methods remain same)

    @Transactional
    fun signupCompany(request: CompanySignupRequest): Long {
        if (userRepository.existsByEmail(request.email)) {
            throw IllegalArgumentException("Email already in use")
        }

        // 1. Create Company
        val company = companyService.createCompany(request.companyName)

        // 2. Create Initial Team (from request)
        val defaultTeam = companyService.createTeam(
            company.id!!, 
            com.onmeet.auth.dto.TeamRequest(request.teamName, "Initial team", "#FFFFFF")
        )

        // 3. Create User (Manager)
        val user = User(
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password),
            name = request.name,
            role = User.Role.MANAGER,
            company = company,
            team = defaultTeam, // Assign to default team
            status = User.UserStatus.ACTIVE
        )
        
        return userRepository.save(user).id!!
    }

    @Transactional
    fun joinCompany(request: JoinRequest): Long {
        // 1. Validate Invitation
        val invitation = invitationService.validateInvitation(request.email, request.code)

        if (userRepository.existsByEmail(request.email)) {
            throw IllegalArgumentException("Email already in use")
        }

        // 2. Create User
        val user = User(
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password),
            name = request.name,
            employeeId = request.employeeId,
            role = invitation.role, // Inherit role from invitation (usually USER)
            company = invitation.company,
            status = User.UserStatus.ACTIVE
        )

        val savedUser = userRepository.save(user)

        // 3. Mark Invitation as used (or delete)
        invitationService.deleteInvitation(invitation.id!!)

        return savedUser.id!!
    }

    @Transactional
    fun login(request: LoginRequest): TokenResponse {
        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.email, request.password)
        )
        
        // Generate Access Token
        val accessToken = jwtTokenProvider.generateToken(authentication)

        // Generate Refresh Token
        val refreshTokenStr = java.util.UUID.randomUUID().toString()
        val authorities = authentication.authorities.joinToString(",") { it.authority }

        // Save to Redis
        val refreshToken = com.onmeet.auth.entity.RefreshToken(
            mobileOrEmail = request.email,
            token = refreshTokenStr,
            authority = authorities
        )
        refreshTokenRepository.save(refreshToken)
        
        return TokenResponse(accessToken, refreshTokenStr)
    }

    fun guestLogin(request: com.onmeet.auth.dto.GuestLoginRequest): TokenResponse {
        val accessToken = jwtTokenProvider.generateGuestToken(request.name, request.meetingId)
        return TokenResponse(accessToken, null)
    }
}
