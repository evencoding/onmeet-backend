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
