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

    // Push Notification Toggle
    @Column(name = "is_push_enabled")
    private boolean isPushEnabled;

    // Meeting Notifications
    @Column(name = "is_meeting_invite_notification")
    private boolean isMeetingInviteNotification;

    @Column(name = "is_meeting_start_notification")
    private boolean isMeetingStartNotification;

    @Column(name = "is_meeting_remind_notification")
    private boolean isMeetingRemindNotification;

    // Misc Notifications
    @Column(name = "is_minutes_completed_notification")
    private boolean isMinutesCompletedNotification;

    @Column(name = "is_system_notice_notification")
    private boolean isSystemNoticeNotification;

    // Do Not Disturb
    @Column(name = "is_do_not_disturb_enabled")
    private boolean isDoNotDisturbEnabled;

    @Column(name = "do_not_disturb_start_time")
    private LocalTime doNotDisturbStartTime;

    @Column(name = "do_not_disturb_end_time")
    private LocalTime doNotDisturbEndTime;

    @Builder
    public NotificationSetting(Long userId, boolean isPushEnabled, boolean isMeetingInviteNotification,
            boolean isMeetingStartNotification, boolean isMeetingRemindNotification,
            boolean isMinutesCompletedNotification, boolean isSystemNoticeNotification,
            boolean isDoNotDisturbEnabled, LocalTime doNotDisturbStartTime, LocalTime doNotDisturbEndTime) {
        this.userId = userId;
        this.isPushEnabled = isPushEnabled;
        this.isMeetingInviteNotification = isMeetingInviteNotification;
        this.isMeetingStartNotification = isMeetingStartNotification;
        this.isMeetingRemindNotification = isMeetingRemindNotification;
        this.isMinutesCompletedNotification = isMinutesCompletedNotification;
        this.isSystemNoticeNotification = isSystemNoticeNotification;
        this.isDoNotDisturbEnabled = isDoNotDisturbEnabled;
        this.doNotDisturbStartTime = doNotDisturbStartTime;
        this.doNotDisturbEndTime = doNotDisturbEndTime;
    }

    public void update(boolean isPushEnabled, boolean isMeetingInviteNotification,
            boolean isMeetingStartNotification, boolean isMeetingRemindNotification,
            boolean isMinutesCompletedNotification, boolean isSystemNoticeNotification,
            boolean isDoNotDisturbEnabled, LocalTime doNotDisturbStartTime, LocalTime doNotDisturbEndTime) {
        this.isPushEnabled = isPushEnabled;
        this.isMeetingInviteNotification = isMeetingInviteNotification;
        this.isMeetingStartNotification = isMeetingStartNotification;
        this.isMeetingRemindNotification = isMeetingRemindNotification;
        this.isMinutesCompletedNotification = isMinutesCompletedNotification;
        this.isSystemNoticeNotification = isSystemNoticeNotification;
        this.isDoNotDisturbEnabled = isDoNotDisturbEnabled;
        this.doNotDisturbStartTime = doNotDisturbStartTime;
        this.doNotDisturbEndTime = doNotDisturbEndTime;
    }
}
