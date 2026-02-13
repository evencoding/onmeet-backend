package com.onmeet.auth.dto

import jakarta.validation.constraints.Size

data class UpdateProfileRequest(
    @field:Size(min = 2, max = 50, message = "Name must be between 2 and 50 characters")
    val name: String?,

    @field:Size(max = 100, message = "Job title must not exceed 100 characters")
    val jobTitle: String?
)
