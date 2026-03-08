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
            "{body}");

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
