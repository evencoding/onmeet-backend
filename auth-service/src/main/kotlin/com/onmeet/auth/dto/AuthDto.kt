package com.onmeet.auth.dto

data class SignupRequest(
    val email: String,
    val password: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class TokenResponse(
    val accessToken: String,
    val tokenType: String = "Bearer"
)

data class LoginResponse(
    val message: String = "Login successful",
    val tokenType: String = "Bearer"
)

data class GuestLoginRequest(
    val name: String,
    val meetingId: String? = null
)
