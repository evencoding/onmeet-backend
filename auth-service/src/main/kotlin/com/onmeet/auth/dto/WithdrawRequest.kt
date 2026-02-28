package com.onmeet.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "회원 탈퇴 요청")
data class WithdrawRequest(
    @Schema(description = "비밀번호", example = "password123!")
    @field:NotBlank(message = "비밀번호는 필수입니다.")
    val password: String,

    @Schema(description = "탈퇴 사유", example = "더 이상 서비스를 사용하지 않음")
    val reason: String? = null
)
