package com.onmeet.notification.dto;

import com.onmeet.notification.entity.NotificationSetting;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSettingDto {
    private boolean pushEnabled;
    private boolean meetingInviteNotification;
    private boolean meetingStartNotification;
    private boolean meetingRemindNotification;
    private boolean minutesCompletedNotification;
    private boolean systemNoticeNotification;
    private boolean doNotDisturbEnabled;
    private LocalTime doNotDisturbStartTime;
    private LocalTime doNotDisturbEndTime;

    public static NotificationSettingDto from(NotificationSetting setting) {
        return NotificationSettingDto.builder()
                .pushEnabled(setting.isPushEnabled())
                .meetingInviteNotification(setting.isMeetingInviteNotification())
                .meetingStartNotification(setting.isMeetingStartNotification())
                .meetingRemindNotification(setting.isMeetingRemindNotification())
                .minutesCompletedNotification(setting.isMinutesCompletedNotification())
                .systemNoticeNotification(setting.isSystemNoticeNotification())
                .doNotDisturbEnabled(setting.isDoNotDisturbEnabled())
                .doNotDisturbStartTime(setting.getDoNotDisturbStartTime())
                .doNotDisturbEndTime(setting.getDoNotDisturbEndTime())
                .build();
    }
}
