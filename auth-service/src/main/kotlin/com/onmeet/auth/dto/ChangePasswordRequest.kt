package com.onmeet.auth.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ChangePasswordRequest(
    @field:NotBlank(message = "Old password is required")
    val oldPassword: String,

    @field:NotBlank(message = "New password is required")
    @field:Size(min = 8, message = "Password must be at least 8 characters long")
    val newPassword: String
)
