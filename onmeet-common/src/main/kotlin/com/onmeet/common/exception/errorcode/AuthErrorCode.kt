package com.onmeet.common.exception.errorcode

import com.onmeet.common.exception.ErrorCode
import org.springframework.http.HttpStatus

/**
 * auth-service 에러 코드 정의.
 */
enum class AuthErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {

    // === 회원가입/로그인 ===
    EMAIL_ALREADY_EXISTS("AUTH_001", "이미 사용 중인 이메일입니다", HttpStatus.CONFLICT),
    INVITATION_NOT_FOUND("AUTH_002", "초대 코드를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    INVALID_INVITATION("AUTH_003", "유효하지 않은 초대 코드이거나 이메일이 일치하지 않습니다", HttpStatus.BAD_REQUEST),
    AUTHENTICATION_FAILED("AUTH_004", "이메일 또는 비밀번호가 일치하지 않습니다", HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN("AUTH_005", "유효하지 않은 리프레시 토큰입니다", HttpStatus.BAD_REQUEST),

    // === 사용자 ===
    USER_NOT_FOUND("AUTH_006", "해당 사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    REQUESTER_NOT_FOUND("AUTH_007", "요청자 정보를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    PROFILE_RESET_FORBIDDEN("AUTH_008", "프로필 이미지 초기화 권한이 없습니다. 매니저만 가능합니다", HttpStatus.FORBIDDEN),
    INVALID_PASSWORD("AUTH_009", "비밀번호가 일치하지 않습니다", HttpStatus.UNAUTHORIZED),
    CURRENT_PASSWORD_MISMATCH("AUTH_010", "현재 비밀번호가 일치하지 않습니다", HttpStatus.UNAUTHORIZED),
    USER_EMAIL_NOT_FOUND("AUTH_011", "해당 이메일로 등록된 사용자가 없습니다", HttpStatus.NOT_FOUND),

    // === 프로필 수정 ===
    PROFILE_UPDATE_FORBIDDEN("AUTH_012", "해당 프로필을 수정할 권한이 없습니다", HttpStatus.FORBIDDEN),
    CROSS_COMPANY_ACCESS("AUTH_013", "다른 회사의 사용자 정보에 접근할 수 없습니다", HttpStatus.FORBIDDEN),
    JOB_TITLE_NOT_FOUND("AUTH_014", "해당 직급을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    JOB_TITLE_COMPANY_MISMATCH("AUTH_015", "해당 직급은 사용자의 회사에 속하지 않습니다", HttpStatus.BAD_REQUEST),

    // === 사용자 관리 ===
    EMPLOYEE_LIST_FORBIDDEN("AUTH_016", "직원 목록은 매니저만 조회할 수 있습니다", HttpStatus.FORBIDDEN),
    USER_STATUS_CHANGE_FORBIDDEN("AUTH_017", "사용자 상태 변경은 매니저만 가능합니다", HttpStatus.FORBIDDEN),
    CROSS_COMPANY_MANAGE_FORBIDDEN("AUTH_018", "다른 회사의 사용자를 관리할 수 없습니다", HttpStatus.FORBIDDEN),

    // === 팀 ===
    TEAM_ALREADY_EXISTS("AUTH_019", "이미 같은 이름의 팀이 존재합니다", HttpStatus.CONFLICT),
    TEAM_MEMBERS_REQUIRED("AUTH_020", "매니저는 팀원 목록과 팀장을 반드시 지정해야 합니다", HttpStatus.BAD_REQUEST),
    LEADER_NOT_IN_MEMBERS("AUTH_021", "팀장은 팀원 목록에 포함되어야 합니다", HttpStatus.BAD_REQUEST),
    TEAM_MEMBER_NOT_FOUND("AUTH_022", "일부 팀원을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    TEAM_MEMBER_COMPANY_MISMATCH("AUTH_023", "모든 팀원은 같은 회사에 속해야 합니다", HttpStatus.BAD_REQUEST),
    TEAM_LEADER_NOT_FOUND("AUTH_024", "팀장으로 지정된 사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    TEAM_NOT_FOUND("AUTH_025", "해당 팀을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    TEAM_APPROVE_FORBIDDEN("AUTH_026", "팀 승인은 매니저만 가능합니다", HttpStatus.FORBIDDEN),
    TEAM_COMPANY_MISMATCH("AUTH_027", "같은 회사의 팀만 처리할 수 있습니다", HttpStatus.BAD_REQUEST),
    TEAM_NOT_PENDING("AUTH_028", "승인 대기 중인 팀만 처리할 수 있습니다", HttpStatus.BAD_REQUEST),
    TEAM_REJECT_FORBIDDEN("AUTH_029", "팀 반려는 매니저만 가능합니다", HttpStatus.FORBIDDEN),
    TEAM_DELEGATE_COMPANY_MISMATCH("AUTH_030", "위임할 팀장은 같은 회사에 속해야 합니다", HttpStatus.BAD_REQUEST),
    TEAM_LEADER_NOT_MEMBER("AUTH_031", "현재 리더가 팀 멤버가 아닙니다", HttpStatus.BAD_REQUEST),
    TEAM_DISSOLVE_COMPANY_MISMATCH("AUTH_032", "다른 회사의 팀을 해체할 수 없습니다", HttpStatus.BAD_REQUEST),
    TEAM_CANCEL_NOT_PENDING("AUTH_033", "승인 대기 중인 팀 요청만 취소할 수 있습니다", HttpStatus.BAD_REQUEST),
    TEAM_CANCEL_FORBIDDEN("AUTH_034", "팀 생성 요청자만 취소할 수 있습니다", HttpStatus.FORBIDDEN),

    // === 회사 ===
    COMPANY_NOT_FOUND("AUTH_035", "해당 회사를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    COMPANY_ALREADY_EXISTS("AUTH_036", "이미 같은 이름의 회사가 존재합니다", HttpStatus.CONFLICT),
    ACTIVE_INVITATION_EXISTS("AUTH_037", "해당 이메일로 이미 유효한 초대가 존재합니다", HttpStatus.CONFLICT),
    INVITATION_EXPIRED("AUTH_038", "초대 코드가 유효하지 않거나 만료되었습니다", HttpStatus.BAD_REQUEST),

    // === 직급 ===
    JOB_TITLE_CROSS_COMPANY("AUTH_039", "다른 회사의 직급에 접근할 수 없습니다", HttpStatus.FORBIDDEN),
    JOB_TITLE_DEFAULT_REQUIRED("AUTH_040", "기본 직급이 최소 1개는 존재해야 합니다", HttpStatus.BAD_REQUEST),
    JOB_TITLE_MANAGE_FORBIDDEN("AUTH_041", "직급 관리는 매니저만 가능합니다", HttpStatus.FORBIDDEN),

    // === 게스트 ===
    GUEST_INVITER_NOT_FOUND("AUTH_042", "초대자 정보를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    GUEST_INVITE_FORBIDDEN("AUTH_043", "해당 회의실의 호스트만 게스트를 초대할 수 있습니다", HttpStatus.FORBIDDEN),
    GUEST_LINK_INVALID("AUTH_044", "유효하지 않거나 존재하지 않는 게스트 초대 링크입니다", HttpStatus.BAD_REQUEST),
    GUEST_LINK_EXPIRED("AUTH_045", "게스트 초대 링크가 만료되었습니다", HttpStatus.BAD_REQUEST),

    // === 내부 인증 ===
    INVALID_INTERNAL_SECRET("AUTH_046", "유효하지 않은 내부 인증 시크릿입니다", HttpStatus.UNAUTHORIZED),
}
