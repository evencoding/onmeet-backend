package com.onmeet.auth.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "직급 정보")
data class JobTitleResponse(
    @Schema(description = "Job Title ID")
    val id: Long,
    @Schema(description = "직급명")
    val name: String,
    @Schema(description = "기본값 여부")
    val isDefault: Boolean
)

@Schema(description = "직급 생성/수정 요청")
data class JobTitleRequest(
    @Schema(description = "직급명", example = "대리")
    val name: String,
    @Schema(description = "기본값 여부", example = "false")
    val isDefault: Boolean = false
)
