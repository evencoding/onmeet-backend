package com.onmeet.notification.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;



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

    // 회의 알림 (초대 및 변경사항)
    @Column(name = "is_meeting_notification", nullable = false)
    private boolean isMeetingNotification = true;

    // 회의록 완성 알림
    @Column(name = "is_minutes_completed_notification", nullable = false)
    private boolean isMinutesCompletedNotification = true;

    // 팀 알림 (멤버 추가, 등등)
    @Column(name = "is_team_notification", nullable = false)
    private boolean isTeamNotification = true;

    @Builder
    public NotificationSetting(Long userId, boolean isMeetingNotification,
            boolean isMinutesCompletedNotification, boolean isTeamNotification) {
        this.userId = userId;
        this.isMeetingNotification = isMeetingNotification;
        this.isMinutesCompletedNotification = isMinutesCompletedNotification;
        this.isTeamNotification = isTeamNotification;
    }

    public void update(boolean isMeetingNotification, boolean isMinutesCompletedNotification, boolean isTeamNotification) {
        this.isMeetingNotification = isMeetingNotification;
        this.isMinutesCompletedNotification = isMinutesCompletedNotification;
        this.isTeamNotification = isTeamNotification;
    }
}
