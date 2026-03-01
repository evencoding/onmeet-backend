package com.onmeet.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size

@Schema(description = "유저 정보 수정 요청")
data class UserProfileUpdateRequest(
    @field:Size(min = 2, max = 50, message = "Name must be between 2 and 50 characters")
    @Schema(description = "이름", example = "Jane Doe")
    val name: String?,

    @field:Size(max = 50, message = "Employee ID must not exceed 50 characters")
    @Schema(description = "사번", example = "EMP-001")
    val employeeId: String?,

    @Schema(description = "직급 ID", example = "1")
    val jobTitleId: Long?
)
