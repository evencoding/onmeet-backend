package com.onmeet.notification.type;

import java.util.Map;

/**
 * NotificationType별 알림 메시지 템플릿을 정의합니다.
 *
 * 사용 가능한 플레이스홀더:
 * {senderName} - 알림을 유발한 사용자 이름 (actorUserId)
 * {receiverName} - 알림을 받는 사용자 이름 (userId)
 * {title} - 알림 제목 (보통 방/회의 이름)
 * {body} - 원본 알림 본문 (커스텀 메시지가 있을 경우)
 */
public enum NotificationTemplate {

    // ── 회의 생성/알림 ──
    MEETING_CREATED(
            "새 회의 생성",
            "{senderName}님이 {title} 회의를 생성했습니다."),

    // ── 회의 초대 관련 ──
    MEETING_INVITATION(
            "회의 초대",
            "{senderName}님이 {title} 회의에 초대했습니다."),


    // ── 회의 진행 관련 ──
    MEETING_STARTED(
            "회의 시작",
            "{title} 회의가 시작되었습니다."),
    SCHEDULE_CREATED(
            "일정 생성",
            "{senderName}님이 {title} 예약 회의를 생성했습니다."),
    SCHEDULE_CHANGED(
            "일정 변경",
            "{title} 회의 일정이 변경되었습니다."),
    SCHEDULE_CANCELLED(
            "일정 취소",
            "{title} 회의가 취소되었습니다."),
    MEETING_REMINDER(
            "회의 리마인더",
            "{title} 회의 시작 30분 전입니다."),

    // ── 기타 ──
    MEETING_TODAY(
            "오늘의 회의",
            "{title} 회의가 오늘 예정되어 있습니다."),
    TEAM_MEMBER_ADDED(
            "팀 멤버 추가",
            "{senderName}님이 팀에 새 멤버를 추가했습니다."),
    SYSTEM(
            "시스템 알림",
            "{body}"),
    EVENT(
            "이벤트",
            "{body}"),

    // ── 회의 초대 상세 ──
    INVITATION_ACCEPTED(
            "초대 수락",
            "{senderName}님이 회의 초대를 수락했습니다."),
    INVITATION_DECLINED(
            "초대 거절",
            "{senderName}님이 회의 초대를 거절했습니다."),
    INVITATION_CANCELLED(
            "초대 취소",
            "{title} 회의 초대가 취소되었습니다."),

    // ── AI 요약 ──
    AI_SUMMARY_PROGRESS(
            "AI 요약 진행 중",
            "{title} 회의의 AI 요약이 진행 중입니다."),
    AI_SUMMARY_COMPLETED(
            "AI 요약 완료",
            "{title} 회의의 AI 요약이 완료되었습니다."),

    // ── 대기실 관련 ──
    WAITING_ROOM_ADMITTED(
            "대기실 입장 승인",
            "대기실 입장이 승인되었습니다."),
    WAITING_ROOM_REJECTED(
            "대기실 입장 거절",
            "대기실 입장이 거절되었습니다."),

    // ── 참가자 관련 ──
    PARTICIPANT_KICKED(
            "회의 퇴장",
            "{title} 회의에서 퇴장되었습니다."),
    PARTICIPANT_JOINED_NOTIFY(
            "참가자 입장",
            "{senderName}님이 {title} 회의에 참가했습니다.");

    private final String defaultTitle;
    private final String bodyTemplate;

    NotificationTemplate(String defaultTitle, String bodyTemplate) {
        this.defaultTitle = defaultTitle;
        this.bodyTemplate = bodyTemplate;
    }

    public String getDefaultTitle() {
        return defaultTitle;
    }

    public String getBodyTemplate() {
        return bodyTemplate;
    }

    /**
     * 플레이스홀더를 실제 값으로 치환하여 알림 본문을 생성합니다.
     *
     * @param params 플레이스홀더 키-값 맵 (예: senderName=홍길동, title=주간회의)
     * @return 치환된 알림 본문
     */
    public String renderBody(Map<String, String> params) {
        String result = bodyTemplate;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    /**
     * NotificationType에 대응하는 템플릿을 찾습니다.
     * 매칭되는 템플릿이 없으면 SYSTEM 템플릿을 반환합니다.
     */
    public static NotificationTemplate fromType(NotificationType type) {
        try {
            return NotificationTemplate.valueOf(type.name());
        } catch (IllegalArgumentException e) {
            return SYSTEM;
        }
    }
}
