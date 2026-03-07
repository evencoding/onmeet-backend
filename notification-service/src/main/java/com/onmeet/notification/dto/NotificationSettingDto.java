package com.onmeet.notification.dto;

import com.onmeet.notification.entity.NotificationSetting;
import com.fasterxml.jackson.annotation.JsonFormat;
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
    private boolean isPushEnabled;
    private boolean isMeetingInviteNotification;
    private boolean isMeetingStartNotification;
    private boolean isMeetingRemindNotification;
    private boolean isMinutesCompletedNotification;
    private boolean isSystemNoticeNotification;
    private boolean isDoNotDisturbEnabled;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime doNotDisturbStartTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime doNotDisturbEndTime;

    public static NotificationSettingDto from(NotificationSetting setting) {
        return NotificationSettingDto.builder()
                .isPushEnabled(setting.isPushEnabled())
                .isMeetingInviteNotification(setting.isMeetingInviteNotification())
                .isMeetingStartNotification(setting.isMeetingStartNotification())
                .isMeetingRemindNotification(setting.isMeetingRemindNotification())
                .isMinutesCompletedNotification(setting.isMinutesCompletedNotification())
                .isSystemNoticeNotification(setting.isSystemNoticeNotification())
                .isDoNotDisturbEnabled(setting.isDoNotDisturbEnabled())
                .doNotDisturbStartTime(setting.getDoNotDisturbStartTime())
                .doNotDisturbEndTime(setting.getDoNotDisturbEndTime())
                .build();
    }
}
