package com.onmeet.auth.dto

data class SignupRequest(
    val email: String,
    val password: String,
    val name: String
)

data class CompanySignupRequest(
    val email: String,
    val password: String,
    val name: String,
    val companyName: String,
    val teamName: String = "General"
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String? = null,
    val tokenType: String = "Bearer"
)

data class LoginResponse(
    val message: String = "Login successful",
    val tokenType: String = "Bearer",
    val accessToken: String? = null,
    val refreshToken: String? = null
)

data class GuestLoginRequest(
    val name: String,
    val meetingId: String? = null
)

data class JoinRequest(
    val email: String,
    val code: String,
    val password: String,
    val name: String,
    val employeeId: String? = null
)

data class CompanyRequest(
    val name: String
)

data class TeamRequest(
    val name: String,
    val description: String?,
    val color: String?
)

data class InvitationRequest(
    @field:jakarta.validation.constraints.NotBlank(message = "Email is required")
    val email: String,
    
    @field:jakarta.validation.constraints.NotBlank(message = "Role is required")
    val role: String = "USER"
)

data class UserResponseDto(
    val id: Long,
    val email: String,
    val name: String,
    val employeeId: String?,
    val role: String,
    val status: String,
    val company: CompanyInfoDto?,
    val team: TeamInfoDto?
)

data class CompanyInfoDto(
    val id: Long,
    val name: String
)

data class TeamInfoDto(
    val id: Long,
    val name: String,
    val color: String?
)

data class RefreshRequest(
    val refreshToken: String
)
