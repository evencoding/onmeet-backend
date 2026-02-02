package com.onmeet.auth.dto

import com.onmeet.auth.entity.User

data class InvitationResponse(
    val email: String,
    val companyName: String,
    val role: User.Role
)
