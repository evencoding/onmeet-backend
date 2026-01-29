package com.onmeet.auth.dto

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

data class CompanySignupRequest(
    val email: String,
    val password: String,
    val name: String,
    val companyName: String,
    val teamName: String = "General" // Default to General if not provided, but allows input
)
