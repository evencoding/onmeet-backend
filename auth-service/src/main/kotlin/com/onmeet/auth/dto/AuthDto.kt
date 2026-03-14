package com.onmeet.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page


@Schema(description = "기업 회원가입 요청")
data class CompanySignupRequest(
    @Schema(description = "이메일 주소", example = "admin@company.com")
    val email: String,

    @Schema(description = "비밀번호", example = "Password123!")
    val password: String,

    @Schema(description = "관리자 이름", example = "John Doe")
    val name: String,

    @Schema(description = "회사명", example = "Acme Corp")
    val companyName: String
)

@Schema(description = "로그인 요청")
data class LoginRequest(
    @Schema(description = "이메일", example = "user@example.com")
    val email: String,
    @Schema(description = "비밀번호", example = "password")
    val password: String,
    @Schema(description = "FCM 디바이스 토큰 (Optional)", example = "fcm-token-xxx")
    val deviceToken: String? = null
)

@Schema(description = "토큰 응답")
data class TokenResponse(
    @Schema(description = "Access Token")
    val accessToken: String,
    @Schema(description = "Refresh Token (Optional)")
    val refreshToken: String? = null,
    @Schema(description = "Token Type", example = "Bearer")
    val tokenType: String = "Bearer"
)

@Schema(description = "로그인 응답")
data class LoginResponse(
    @Schema(description = "응답 메시지", example = "Login successful")
    val message: String = "Login successful",
    @Schema(description = "Token Type", example = "Bearer")
    val tokenType: String = "Bearer"
)

@Schema(description = "게스트 로그인 요청")
data class GuestLoginRequest(
    @Schema(description = "게스트 이름", example = "Guest User")
    val name: String,
    @Schema(description = "참여할 미팅 ID (Optional)", example = "meeting-123")
    val meetingId: String? = null
)

@Schema(description = "사원 가입 요청")
data class JoinRequest(
    @Schema(description = "이메일", example = "employee@company.com")
    val email: String,
    @Schema(description = "초대 코드", example = "INV-123456")
    val code: String,
    @Schema(description = "비밀번호", example = "Password123!")
    val password: String,
    @Schema(description = "이름", example = "Jane Doe")
    val name: String,
    @Schema(description = "사번 (Optional)", example = "EMP-001")
    val employeeId: String? = null
)

@Schema(description = "회사 정보 수정 요청")
data class CompanyRequest(
    @Schema(description = "회사명")
    val name: String
)

@Schema(description = "회사 정보 수정 요청 (부분 수정)")
data class UpdateCompanyRequest(
    @Schema(description = "변경할 회사명", example = "New Company Name")
    val name: String?
)

@Schema(description = "회사 정보 응답")
data class CompanyResponse(
    @Schema(description = "Company ID")
    val id: Long,
    @Schema(description = "회사명")
    val name: String,
    @Schema(description = "상태")
    val status: String
)

@Schema(description = "팀 생성/수정 요청")
data class TeamRequest(
    @Schema(description = "팀 이름", example = "Development")
    val name: String,
    @Schema(description = "팀 설명", example = "Backend Development Team")
    val description: String?,
    @Schema(description = "팀 색상 (Hex Code)", example = "#FF5733")
    val color: String?,
    @Schema(description = "팀원 ID 목록 (MANAGER 권한 전용)", example = "[1, 2, 3]")
    val memberIds: List<Long>? = null,
    @Schema(description = "팀장 ID (MANAGER 권한 전용, memberIds 중 한 명이어야 함)", example = "1")
    val leaderId: Long? = null
)

data class TeamRejectRequest(val reason: String?)

@Schema(description = "초대 요청")
data class InvitationRequest(
    @field:jakarta.validation.constraints.NotEmpty(message = "At least one email is required")
    @field:jakarta.validation.constraints.Size(max = 100, message = "Maximum 100 emails allowed per request")
    @Schema(description = "초대할 이메일 리스트", example = "[\"user1@company.com\", \"user2@company.com\"]")
    val emails: List<@jakarta.validation.constraints.Email(message = "Invalid email format") String>
)

@Schema(description = "유저 정보 상세 응답")
data class UserResponseDto(
    @Schema(description = "User ID")
    val id: Long,
    @Schema(description = "이메일")
    val email: String,
    @Schema(description = "이름")
    val name: String,
    @Schema(description = "사번")
    val employeeId: String?,
    @Schema(description = "권한 목록")
    val roles: Set<String>,
    @Schema(description = "상태")
    val status: String,
    @Schema(description = "소속 회사 정보")
    val company: CompanyInfoDto?,
    @Schema(description = "직급 정보")
    val jobTitle: JobTitleResponse?,
    @Schema(description = "소속 팀 목록")
    val teams: List<TeamInfoDto>,
    @Schema(description = "프로필 이미지 ID")
    val profileImageId: Long?,
    @Schema(description = "비밀번호 초기화 여부 (임시 비밀번호 발급 시 true)")
    val isPasswordReset: Boolean = false
)

@Schema(description = "회사 정보 요약")
data class CompanyInfoDto(
    @Schema(description = "Company ID")
    val id: Long,
    @Schema(description = "회사명")
    val name: String
)

@Schema(description = "팀 정보 요약")
data class TeamInfoDto(
    @Schema(description = "Team ID")
    val id: Long,
    @Schema(description = "팀 이름")
    val name: String,
    @Schema(description = "팀 색상")
    val color: String?
)

@Schema(description = "유저 권한 및 소속 정보 응답")
data class UserPermissionResponse(
    @Schema(description = "User ID")
    val userId: Long,
    @Schema(description = "권한 목록")
    val roles: Set<String>,
    @Schema(description = "소속 회사 ID")
    val companyId: Long?,
    @Schema(description = "소속 팀 ID 목록")
    val teamIds: List<Long>
)

@Schema(description = "토큰 갱신 요청")
data class RefreshRequest(
    @Schema(description = "Refresh Token (Optional if cookie is present)")
    val refreshToken: String
)

@Schema(description = "비밀번호 찾기 요청")
data class FindPasswordRequest(
    @Schema(description = "이메일", example = "user@example.com")
    @field:jakarta.validation.constraints.Email(message = "Invalid email format")
    @field:jakarta.validation.constraints.NotBlank(message = "Email is required")
    val email: String
)

data class EmailMessage(
    val to: String,
    val subject: String,
    val templateName: String,
    val variables: Map<String, Any>
)
@Schema(description = "페이징 응답")
data class PageResponse<T>(
    @Schema(description = "데이터 목록")
    val content: List<T>,
    @Schema(description = "현재 페이지 번호 (0-indexed)")
    val pageNumber: Int,
    @Schema(description = "페이지 크기")
    val pageSize: Int,
    @Schema(description = "전체 요소 수")
    val totalElements: Long,
    @Schema(description = "전체 페이지 수")
    val totalPages: Int,
    @Schema(description = "마지막 페이지 여부")
    val last: Boolean
) {
    companion object {
        fun <T, R> from(page: Page<T>, mapper: (T) -> R): PageResponse<R> {
            return PageResponse(
                content = page.content.map(mapper),
                pageNumber = page.number,
                pageSize = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
                last = page.isLast
            )
        }
    }
}
