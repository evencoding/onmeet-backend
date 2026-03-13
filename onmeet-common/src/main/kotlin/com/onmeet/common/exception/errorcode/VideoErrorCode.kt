package com.onmeet.common.exception.errorcode

import com.onmeet.common.exception.ErrorCode
import org.springframework.http.HttpStatus

/**
 * video-service 에러 코드 정의.
 */
enum class VideoErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {

    // === 회의실 생성/조회 ===
    TEAM_ID_REQUIRED("VIDEO_001", "TEAM 접근 범위 설정 시 teamId는 필수입니다", HttpStatus.BAD_REQUEST),
    TEAM_NOT_FOUND("VIDEO_002", "존재하지 않는 팀입니다", HttpStatus.NOT_FOUND),
    HOST_NOT_TEAM_MEMBER("VIDEO_003", "호스트는 해당 팀의 멤버여야 합니다", HttpStatus.FORBIDDEN),
    ROOM_NOT_FOUND("VIDEO_004", "존재하지 않는 회의실입니다", HttpStatus.NOT_FOUND),
    ACTIVE_ROOM_DELETE_DENIED("VIDEO_005", "진행 중인 회의실은 삭제할 수 없습니다", HttpStatus.BAD_REQUEST),
    ROOM_CODE_NOT_FOUND("VIDEO_006", "해당 코드의 회의실을 찾을 수 없습니다", HttpStatus.NOT_FOUND),

    // === 입장/퇴장 ===
    ROOM_ALREADY_ENDED("VIDEO_007", "이미 종료된 회의실입니다", HttpStatus.BAD_REQUEST),
    ALREADY_JOINED("VIDEO_008", "이미 입장한 회의실입니다", HttpStatus.CONFLICT),
    NOT_TEAM_MEMBER("VIDEO_009", "팀 멤버만 입장할 수 있는 회의실입니다", HttpStatus.FORBIDDEN),
    WRONG_PASSWORD("VIDEO_010", "회의실 비밀번호가 올바르지 않습니다", HttpStatus.FORBIDDEN),
    ROOM_FULL("VIDEO_011", "회의실 최대 참가자 수에 도달했습니다", HttpStatus.BAD_REQUEST),
    NOT_A_PARTICIPANT("VIDEO_012", "해당 회의실의 참가자가 아닙니다", HttpStatus.NOT_FOUND),

    // === 회의 상태 ===
    ROOM_NOT_WAITING("VIDEO_013", "WAITING 상태의 회의실만 시작할 수 있습니다", HttpStatus.BAD_REQUEST),
    ROOM_NOT_ACTIVE("VIDEO_014", "진행 중인 회의실만 종료할 수 있습니다", HttpStatus.BAD_REQUEST),

    // === 권한 ===
    HOST_ONLY("VIDEO_015", "호스트만 수행할 수 있는 작업입니다", HttpStatus.FORBIDDEN),
    HOST_OR_COHOST_ONLY("VIDEO_016", "호스트 또는 공동 호스트만 수행할 수 있는 작업입니다", HttpStatus.FORBIDDEN),

    // === 설정/태그/즐겨찾기 ===
    SETTINGS_NOT_FOUND("VIDEO_017", "회의실 설정 정보를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    TAG_ALREADY_EXISTS("VIDEO_018", "이미 존재하는 태그입니다", HttpStatus.CONFLICT),
    TAG_NOT_FOUND("VIDEO_019", "존재하지 않는 태그입니다", HttpStatus.NOT_FOUND),
    FAVORITE_ALREADY_EXISTS("VIDEO_020", "이미 즐겨찾기에 추가된 회의실입니다", HttpStatus.CONFLICT),
    FAVORITE_NOT_FOUND("VIDEO_021", "즐겨찾기에 등록되지 않은 회의실입니다", HttpStatus.NOT_FOUND),

    // === 예약 ===
    SCHEDULE_CONFLICT("VIDEO_022", "해당 시간대에 이미 예약된 회의가 있습니다", HttpStatus.CONFLICT),
    NOT_SCHEDULED_ROOM("VIDEO_023", "예약 회의실만 일정을 변경할 수 있습니다", HttpStatus.BAD_REQUEST),
    SCHEDULE_CHANGE_DENIED("VIDEO_024", "시작 또는 종료된 회의실의 일정은 변경할 수 없습니다", HttpStatus.BAD_REQUEST),
    SCHEDULE_PAST_TIME("VIDEO_025", "예약 시간은 현재 시간 이후여야 합니다", HttpStatus.BAD_REQUEST),
    CANCEL_DENIED("VIDEO_026", "시작 또는 종료된 회의실은 취소할 수 없습니다", HttpStatus.BAD_REQUEST),
    REMINDER_NOT_SCHEDULED("VIDEO_027", "예약 회의실만 리마인더를 발송할 수 있습니다", HttpStatus.BAD_REQUEST),
    REMINDER_ROOM_ENDED("VIDEO_028", "종료된 회의실에는 리마인더를 발송할 수 없습니다", HttpStatus.BAD_REQUEST),

    // === 참가자 관리 ===
    PARTICIPANT_NOT_FOUND("VIDEO_029", "해당 참가자를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    HOST_ROLE_CHANGE_DENIED("VIDEO_030", "호스트의 역할은 변경할 수 없습니다", HttpStatus.FORBIDDEN),
    HOST_KICK_DENIED("VIDEO_031", "호스트는 강제 퇴장시킬 수 없습니다", HttpStatus.FORBIDDEN),
    WAITING_PARTICIPANT_NOT_FOUND("VIDEO_032", "대기 중인 참가자를 찾을 수 없습니다", HttpStatus.NOT_FOUND),

    // === 초대 ===
    INVITE_ROOM_ENDED("VIDEO_033", "종료된 회의실에는 초대를 보낼 수 없습니다", HttpStatus.BAD_REQUEST),
    SELF_INVITE_DENIED("VIDEO_034", "자기 자신을 초대할 수 없습니다", HttpStatus.BAD_REQUEST),
    INVITATION_ALREADY_PENDING("VIDEO_035", "해당 사용자에게 이미 대기 중인 초대장이 있습니다", HttpStatus.CONFLICT),
    INVITEE_NOT_FOUND("VIDEO_036", "존재하지 않는 사용자입니다", HttpStatus.NOT_FOUND),
    BULK_INVITE_USER_NOT_FOUND("VIDEO_037", "존재하지 않는 사용자가 포함되어 있습니다", HttpStatus.NOT_FOUND),
    INVITATION_NOT_FOUND("VIDEO_038", "초대장을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    NOT_INVITEE("VIDEO_039", "초대받은 당사자만 처리할 수 있습니다", HttpStatus.FORBIDDEN),
    INVITATION_ALREADY_PROCESSED("VIDEO_040", "이미 처리된 초대장입니다", HttpStatus.BAD_REQUEST),

    // === 녹음 ===
    RECORDING_ROOM_NOT_ACTIVE("VIDEO_041", "진행 중인 회의실에서만 녹음을 시작할 수 있습니다", HttpStatus.BAD_REQUEST),
    RECORDING_DISABLED("VIDEO_042", "이 회의실은 녹음이 비활성화되어 있습니다", HttpStatus.FORBIDDEN),
    RECORDING_ALREADY_IN_PROGRESS("VIDEO_043", "녹음이 이미 진행 중입니다", HttpStatus.CONFLICT),
    ACTIVE_RECORDING_NOT_FOUND("VIDEO_044", "진행 중인 녹음을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    RECORDING_NOT_FOUND("VIDEO_045", "녹음 파일을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    RECORDING_NOT_COMPLETED("VIDEO_046", "녹음이 아직 완료되지 않았습니다", HttpStatus.BAD_REQUEST),
    RECORDING_S3_NOT_READY("VIDEO_047", "녹음 파일이 아직 업로드되지 않았습니다", HttpStatus.NOT_FOUND),

    // === 화면 공유 ===
    SCREEN_SHARE_ROOM_NOT_ACTIVE("VIDEO_048", "진행 중인 회의실에서만 화면 공유를 시작할 수 있습니다", HttpStatus.BAD_REQUEST),
    SCREEN_SHARE_NOT_ALLOWED("VIDEO_049", "이 회의실은 화면 공유가 허용되지 않습니다", HttpStatus.FORBIDDEN),
    NOT_JOINED_PARTICIPANT("VIDEO_050", "회의실에 참가 중인 사용자가 아닙니다", HttpStatus.NOT_FOUND),
    ALREADY_SHARING("VIDEO_051", "이미 화면을 공유 중입니다", HttpStatus.CONFLICT),
    NOT_SHARING("VIDEO_052", "현재 화면을 공유 중이지 않습니다", HttpStatus.BAD_REQUEST),
    TARGET_NOT_SHARING("VIDEO_053", "대상 참가자가 화면을 공유 중이지 않습니다", HttpStatus.BAD_REQUEST),
    SCREEN_SHARE_SERIALIZE_FAILED("VIDEO_054", "화면 공유 메시지 직렬화에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // === 채팅 ===
    ROOM_ID_OR_CODE_REQUIRED("VIDEO_055", "roomId 또는 roomCode 중 하나는 필수입니다", HttpStatus.BAD_REQUEST),
    NOT_PARTICIPANT_CHAT("VIDEO_056", "해당 회의실의 참가자가 아닙니다", HttpStatus.FORBIDDEN),
    CHAT_SERIALIZE_FAILED("VIDEO_057", "채팅 메시지 직렬화에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // === 대기실 ===
    WAITING_ROOM_NOT_REGISTERED("VIDEO_058", "대기실에 등록된 참가자가 아닙니다", HttpStatus.NOT_FOUND),
}
