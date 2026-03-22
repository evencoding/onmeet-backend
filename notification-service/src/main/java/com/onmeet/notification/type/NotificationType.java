package com.onmeet.notification.type;

public enum NotificationType {
    // 회의 생성/알림
    MEETING_CREATED,
    MEETING_TODAY,
    MEETING_STARTED,

    // 팀/시스템
    TEAM_MEMBER_ADDED,
    SYSTEM,
    EVENT,

    // 회의 초대 관련
    MEETING_INVITATION,

    // 초대 응답
    INVITATION_ACCEPTED,
    INVITATION_DECLINED,
    INVITATION_CANCELLED,

    // 참가자 관련
    PARTICIPANT_KICKED,
    WAITING_ROOM_ADMITTED,
    WAITING_ROOM_REJECTED,
    PARTICIPANT_JOINED_NOTIFY,

    // 일정 관련
    SCHEDULE_CREATED,
    SCHEDULE_CHANGED,
    SCHEDULE_CANCELLED,
    MEETING_REMINDER,

    // AI 요약 관련
    AI_SUMMARY_PROGRESS,
    AI_SUMMARY_COMPLETED,
}
