package com.onmeet.notification.type;

public enum NotificationType {
    MEETING_TODAY,
    MEETING_STARTED,
    TEAM_MEMBER_ADDED,
    SYSTEM,
    EVENT,

    // 회의 초대 관련
    MEETING_INVITATION,

    // 회의 진행 관련
    SCHEDULE_CREATED,
    SCHEDULE_CHANGED,
    SCHEDULE_CANCELLED,
    MEETING_REMINDER,

    // AI 요약 관련
    AI_SUMMARY_PROGRESS,
    AI_SUMMARY_COMPLETED
}
