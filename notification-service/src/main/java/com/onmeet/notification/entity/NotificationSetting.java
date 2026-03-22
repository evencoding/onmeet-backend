package com.onmeet.notification.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notification_setting")
public class NotificationSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    // 전체 푸시 알림 ON/OFF
    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled = true;

    // 회의 초대 알림 (기존 is_meeting_notification 컬럼 재사용)
    @Column(name = "is_meeting_notification", nullable = false)
    private boolean meetingInviteNotification = true;

    // 회의 시작 알림
    @Column(name = "meeting_start_notification", nullable = false)
    private boolean meetingStartNotification = true;

    // 회의 리마인더 알림
    @Column(name = "meeting_remind_notification", nullable = false)
    private boolean meetingRemindNotification = true;

    // 회의록 완성 알림 (기존 is_minutes_completed_notification 컬럼 재사용)
    @Column(name = "is_minutes_completed_notification", nullable = false)
    private boolean minutesCompletedNotification = true;

    // 시스템/공지 알림 (기존 is_team_notification 컬럼 재사용)
    @Column(name = "is_team_notification", nullable = false)
    private boolean systemNoticeNotification = true;

    // 방해금지 모드
    @Column(name = "do_not_disturb_enabled", nullable = false)
    private boolean doNotDisturbEnabled = false;

    @Column(name = "do_not_disturb_start_time")
    private LocalTime doNotDisturbStartTime;

    @Column(name = "do_not_disturb_end_time")
    private LocalTime doNotDisturbEndTime;

    @Builder
    public NotificationSetting(Long userId, boolean pushEnabled,
            boolean meetingInviteNotification, boolean meetingStartNotification,
            boolean meetingRemindNotification, boolean minutesCompletedNotification,
            boolean systemNoticeNotification, boolean doNotDisturbEnabled,
            LocalTime doNotDisturbStartTime, LocalTime doNotDisturbEndTime) {
        this.userId = userId;
        this.pushEnabled = pushEnabled;
        this.meetingInviteNotification = meetingInviteNotification;
        this.meetingStartNotification = meetingStartNotification;
        this.meetingRemindNotification = meetingRemindNotification;
        this.minutesCompletedNotification = minutesCompletedNotification;
        this.systemNoticeNotification = systemNoticeNotification;
        this.doNotDisturbEnabled = doNotDisturbEnabled;
        this.doNotDisturbStartTime = doNotDisturbStartTime;
        this.doNotDisturbEndTime = doNotDisturbEndTime;
    }

    public void update(boolean pushEnabled, boolean meetingInviteNotification,
            boolean meetingStartNotification, boolean meetingRemindNotification,
            boolean minutesCompletedNotification, boolean systemNoticeNotification,
            boolean doNotDisturbEnabled, LocalTime doNotDisturbStartTime,
            LocalTime doNotDisturbEndTime) {
        this.pushEnabled = pushEnabled;
        this.meetingInviteNotification = meetingInviteNotification;
        this.meetingStartNotification = meetingStartNotification;
        this.meetingRemindNotification = meetingRemindNotification;
        this.minutesCompletedNotification = minutesCompletedNotification;
        this.systemNoticeNotification = systemNoticeNotification;
        this.doNotDisturbEnabled = doNotDisturbEnabled;
        this.doNotDisturbStartTime = doNotDisturbStartTime;
        this.doNotDisturbEndTime = doNotDisturbEndTime;
    }
}
