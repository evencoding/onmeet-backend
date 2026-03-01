package com.onmeet.auth.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "내부 서비스용 사용자 정보 (간소화)")
data class UserInfoDto(
    @Schema(description = "User ID")
    val userId: Long,
    @Schema(description = "사용자 이름")
    val name: String,
    @Schema(description = "이메일")
    val email: String,
    @Schema(description = "프로필 이미지 ID")
    val profileImageId: Long?
)

@Schema(description = "다중 사용자 정보 조회 요청")
data class BatchUserInfoRequest(
    @Schema(description = "조회할 사용자 ID 목록")
    val userIds: List<Long>
)

@Schema(description = "다중 사용자 정보 조회 응답")
data class BatchUserInfoResponse(
    @Schema(description = "사용자 정보 목록")
    val users: List<UserInfoDto>
)
