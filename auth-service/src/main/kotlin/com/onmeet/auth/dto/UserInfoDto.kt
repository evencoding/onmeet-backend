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

@Schema(description = "사용자 존재 여부 확인 응답")
data class UserExistsResponse(
    @Schema(description = "사용자 ID")
    val userId: Long,
    @Schema(description = "존재 여부")
    val exists: Boolean
)

@Schema(description = "다중 사용자 존재 여부 확인 요청")
data class BatchUserExistsRequest(
    @Schema(description = "확인할 사용자 ID 목록")
    val userIds: List<Long>
)

@Schema(description = "다중 사용자 존재 여부 확인 응답")
data class BatchUserExistsResponse(
    @Schema(description = "사용자 존재 여부 목록")
    val users: List<UserExistsResponse>
)

@Schema(description = "팀 존재 여부 확인 응답")
data class TeamExistsResponse(
    @Schema(description = "팀 ID")
    val teamId: Long,
    @Schema(description = "존재 여부")
    val exists: Boolean
)

@Schema(description = "팀 멤버십 확인 요청")
data class TeamMembershipRequest(
    @Schema(description = "팀 ID")
    val teamId: Long,
    @Schema(description = "사용자 ID")
    val userId: Long
)

@Schema(description = "팀 멤버십 확인 응답")
data class TeamMembershipResponse(
    @Schema(description = "팀 ID")
    val teamId: Long,
    @Schema(description = "사용자 ID")
    val userId: Long,
    @Schema(description = "팀 멤버 여부")
    val isMember: Boolean
)

@Schema(description = "FCM 디바이스 토큰 일괄 조회 요청")
data class BatchFcmTokenRequest(
    @Schema(description = "조회할 사용자 ID 목록")
    val userIds: List<Long>
)

@Schema(description = "FCM 디바이스 토큰 일괄 조회 응답")
data class BatchFcmTokenResponse(
    @Schema(description = "사용자 ID별 FCM 토큰 목록 (토큰이 없는 사용자는 포함되지 않음)")
    val tokens: Map<Long, List<String>>
)
