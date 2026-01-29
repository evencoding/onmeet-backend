package com.onmeet.auth.dto

data class CompanyRequest(
    val name: String
)

data class TeamRequest(
    val name: String,
    val description: String?,
    val color: String?
)

data class InvitationRequest(
    val email: String,
    val role: String = "USER"
)

data class JoinRequest(
    val email: String,
    val code: String,
    val password: String,
    val name: String,
    val employeeId: String?
)
