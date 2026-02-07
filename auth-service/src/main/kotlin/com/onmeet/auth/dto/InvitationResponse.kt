package com.onmeet.auth.dto

import com.onmeet.auth.entity.User
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "초대 검증 응답")
data class InvitationResponse(
    @Schema(description = "초대된 이메일")
    val email: String,
    @Schema(description = "초대된 회사명")
    val companyName: String,
    @Schema(description = "부여된 권한")
    val role: User.Role
)
